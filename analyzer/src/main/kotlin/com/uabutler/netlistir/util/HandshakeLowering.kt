package com.uabutler.netlistir.util

import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode

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

    fun lower(modules: List<Module>): List<Module> = modules.map { module ->
        val hasPrimitives = module.getBodyNodes().any { it is PredefinedFunctionNode && it.predefinedFunction is ValidDelayFunction }
        if (hasPrimitives) module.toMutableModule().also(::lowerModule) else module
    }

    private fun lowerModule(module: MutableModule) {
        module.getBodyNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .forEach { node ->
                when (val function = node.predefinedFunction) {
                    is ValidDelayFunction -> lowerValidDelay(module, node, function.depth)
                    else -> {}
                }
            }
    }

    // A chain of `depth` one-bit registers between the valid's source and its sinks; depth 0 is a wire.
    private fun lowerValidDelay(module: MutableModule, node: PredefinedFunctionNode, depth: Int) {
        val input = node.inputWires().single()
        val output = node.outputWires().single()
        val source = module.getConnectionForInputWire(input).source
        val sinks = module.getConnectionsForOutputWire(output).map { it.sink }

        module.disconnect(input)
        sinks.forEach { module.disconnect(it) }
        module.removeNode(node)

        val delayed = (1..depth).fold(source) { previous: OutputWire, stage ->
            val register = RegisterFunction(WireInterfaceStructure)
            val registerNode = PredefinedFunctionNode(
                identifier = "${node.name()}\$stage$stage",
                parentModule = module,
                inputWireVectorGroupsBuilder = { n -> register.inputs.map { it.toInputWireVectorGroup(n) } },
                outputWireVectorGroupsBuilder = { n -> register.outputs.map { it.toOutputWireVectorGroup(n) } },
                predefinedFunction = register,
            )
            module.addBodyNode(registerNode)
            module.connect(registerNode.inputWires().single(), previous)
            registerNode.outputWires().single()
        }

        sinks.forEach { module.connect(it, delayed) }
    }
}
