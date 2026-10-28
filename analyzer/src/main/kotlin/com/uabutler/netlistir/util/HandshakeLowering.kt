package com.uabutler.netlistir.util

import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import java.math.BigInteger

/**
 * Expands layer-3 handshake primitives (see the end of PredefinedFunction.kt) into ordinary gates and
 * registers. Called late on purpose: the compiler runs it after its transformer pipeline, just before
 * Verilog emission, and simengine runs it when building an Engine - so the primitives stay first-class
 * through everything else, retiming included, and neither of those two needs to know they exist.
 *
 * A module with no handshake primitives is returned untouched (not copied), so designs that don't use
 * layer 3 compile exactly as before.
 */
object HandshakeLowering {

    private val PredefinedFunction.isHandshake
        get() = this is ValidDelayFunction || this is IslandControlFunction || this is LazyForkFunction

    fun lower(modules: List<Module>): List<Module> = modules.map { module ->
        val hasPrimitives = module.getBodyNodes().any { it is PredefinedFunctionNode && it.predefinedFunction.isHandshake }
        if (hasPrimitives) module.toMutableModule().also(::lowerModule) else module
    }

    private fun lowerModule(module: MutableModule) {
        module.getBodyNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction.isHandshake }
            .forEach { node ->
                // Detach the primitive, keeping where each input came from and where each output went.
                val sources = node.inputWires().map { module.getConnectionForInputWire(it).source }
                val sinks = node.outputWires().map { output -> module.getConnectionsForOutputWire(output).map { it.sink } }
                node.inputWires().forEach { module.disconnect(it) }
                sinks.flatten().forEach { module.disconnect(it) }
                module.removeNode(node)

                val gates = Gates(module, node.name())
                val outputs = when (val function = node.predefinedFunction) {
                    is ValidDelayFunction -> listOf(gates.delay(sources.single(), function.depth))
                    is IslandControlFunction -> islandControl(gates, function, sources)
                    is LazyForkFunction -> lazyFork(gates, function, sources)
                    else -> error("not a handshake primitive: $function")
                }

                outputs.zip(sinks).forEach { (output, outputSinks) -> outputSinks.forEach { module.connect(it, output) } }
            }
    }

    // See IslandControlFunction for the behaviour this implements. Returns its outputs, in IO order.
    private fun islandControl(gates: Gates, function: IslandControlFunction, sources: List<OutputWire>): List<OutputWire> {
        val inValid = (0 until function.inputCount).map { sources[function.inValid(it)] }
        val outReady = (0 until function.outputCount).map { sources[function.outReady(it)] }
        val allValid = gates.and(inValid)
        val produced = (0 until function.outputCount).map { if (function.producedByCallee) sources[function.produced(it)] else allValid }

        // One done bit per output; with a single output it is never set, so it is just 0.
        val doneRegisters = if (function.outputCount > 1) List(function.outputCount) { gates.register() } else emptyList()
        val done = doneRegisters.map { it.second }.ifEmpty { listOf(gates.constant(false)) }

        val outValid = produced.zip(done) { p, d -> gates.and(listOf(p, gates.not(d))) }
        val taken = outValid.zip(outReady) { v, r -> gates.and(listOf(v, r)) }
        val settled = (0 until function.outputCount).map { gates.or(listOf(done[it], taken[it], gates.not(produced[it]))) }
        val fire = gates.and(listOf(allValid) + settled)

        doneRegisters.forEachIndexed { o, (next, _) ->
            gates.drive(next, gates.and(listOf(gates.not(fire), gates.or(listOf(done[o], taken[o])))))
        }

        return List(function.inputCount) { fire } + outValid
    }

    // See LazyForkFunction. Returns its outputs, in IO order.
    private fun lazyFork(gates: Gates, function: LazyForkFunction, sources: List<OutputWire>): List<OutputWire> {
        val inValid = sources[0]
        val outReady = (0 until function.branchCount).map { sources[1 + it] }
        val outValid = (0 until function.branchCount).map { k -> gates.and(listOf(inValid) + outReady.filterIndexed { j, _ -> j != k }) }
        return listOf(gates.and(outReady)) + outValid
    }

    /** Builds one-bit logic into a module, naming each new node after the primitive it replaces. */
    private class Gates(private val module: MutableModule, private val prefix: String) {
        private var count = 0

        private fun node(function: PredefinedFunction, inputs: List<OutputWire>): PredefinedFunctionNode {
            val node = PredefinedFunctionNode(
                identifier = "$prefix\$${count++}",
                parentModule = module,
                inputWireVectorGroupsBuilder = { n -> function.inputs.map { it.toInputWireVectorGroup(n) } },
                outputWireVectorGroupsBuilder = { n -> function.outputs.map { it.toOutputWireVectorGroup(n) } },
                predefinedFunction = function,
            )
            module.addBodyNode(node)
            node.inputWires().zip(inputs).forEach { (wire, source) -> module.connect(wire, source) }
            return node
        }

        fun constant(value: Boolean): OutputWire =
            node(LiteralFunction(1, if (value) BigInteger.ONE else BigInteger.ZERO), emptyList()).outputWires().single()

        fun not(a: OutputWire): OutputWire = node(LogicalNotFunction, listOf(a)).outputWires().single()

        fun and(terms: List<OutputWire>): OutputWire =
            terms.reduceOrNull { a, b -> node(LogicalAndFunction, listOf(a, b)).outputWires().single() } ?: constant(true)

        fun or(terms: List<OutputWire>): OutputWire =
            terms.reduceOrNull { a, b -> node(LogicalOrFunction, listOf(a, b)).outputWires().single() } ?: constant(false)

        /** A one-bit register (resetting to 0): its not-yet-driven input, and its output. */
        fun register(): Pair<InputWire, OutputWire> {
            val register = node(RegisterFunction(WireInterfaceStructure), emptyList())
            return register.inputWires().single() to register.outputWires().single()
        }

        fun drive(input: InputWire, source: OutputWire) = module.connect(input, source)

        /** `source` delayed by `depth` cycles; depth 0 is the wire itself. */
        fun delay(source: OutputWire, depth: Int): OutputWire = (1..depth).fold(source) { previous, _ ->
            val (next, current) = register()
            drive(next, previous)
            current
        }
    }
}
