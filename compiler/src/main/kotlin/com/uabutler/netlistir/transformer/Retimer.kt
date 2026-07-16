package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.transformer.util.HierarchicalRetimer
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.util.FastSolver
import com.uabutler.util.graph.util.MinimalRegisterSolver
import com.uabutler.util.graph.util.Retiming
import com.uabutler.verilogir.builder.creator.util.Identifier

/* TODO: Interface
 *   I think we want to control retiming via these command line argument
 *      -retime-for-clock-period [MIN|int]
 *      -retime-for-min-register-count
 *      -retime-maintains-timing
 */

class Retimer(
    val mode: Mode,
    val delay: PropagationDelay,
    val targetClockPeriod: Int?,
    val minimizeRegisterCount: Boolean,
    val maintainTiming: Boolean,
): Transformer {

    enum class Mode(val mode: String) {
        MONOLITH("monolith"),
        HIERARCHICAL("hierarchical"),
    }

    companion object {
        fun retimeModuleFilter(module: MutableModule): Boolean {
            return module.getNodes().any {
                it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction
            }
        }
    }

    private fun recordModuleStats(
        name: String,
        module: MutableModule,
    ) {
        Logger.trace {
            val registerWires = module.getBodyNodes().sumOf { it.inputWires().size }
            "$name body wire count: $registerWires"
        }

        Logger.trace {
            val registerWires =  module.getBodyNodes()
                .filterIsInstance<PredefinedFunctionNode>()
                .filter { it.predefinedFunction is RegisterFunction }
                .sumOf { it.inputWires().size }
            "$name register wire count: $registerWires"
        }
    }

    private fun recordGraphStats(
        name: String,
        graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>,
    ) {
        Logger.trace { "$name leiserson graph register count: ${graph.edges.sumOf { it.weight }}" }
        Logger.trace { "$name leiserson graph clock period: ${graph.computeClockPeriod()}" }
        Logger.run("$name leiserson graph edge weights") {
            graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                Logger.trace { "${edges.size} edges with weight $weight" }
            }
        }

        Logger.ifTrace {
            Logger.start("$name graph path analysis", Logger.Level.TRACE)
            val inputNodes = graph.nodes.filter { it.value is InputNode }.toList()
            val outputNodes = graph.nodes.filter { it.value is OutputNode }.toSet()

            Logger.start("Shortest combinational delays", Logger.Level.TRACE)
            inputNodes.forEach { inputNode ->
                val fastestConnectionsToOutputs = graph.findFastestConnectionsFromNode(inputNode)
                    .filter { it.sink in outputNodes }

                fastestConnectionsToOutputs.forEach { connection ->
                    Logger.trace { "${connection.source.value.name()} -> ${connection.sink.value.name()}: ${connection.registerCount}" }
                }
            }
            Logger.finish()

            Logger.start("All combinational delays", Logger.Level.TRACE)
            inputNodes.forEach { inputNode ->
                val fastestConnectionsToOutputs = graph.registerDelaysFrom(inputNode)
                    .filter { it.key in outputNodes }

                fastestConnectionsToOutputs.forEach { connection ->
                    Logger.trace { "${inputNode.value.name()} -> ${connection.key.value.name()}: ${connection.value}" }
                }
            }
            Logger.finish()

            Logger.start("Register size count", Logger.Level.TRACE)
            val registerSizeCount = mutableMapOf<Int, Int>()

            graph.edges.forEach { edge ->
                val registerSize = edge.value.count()

                val registerCount = registerSizeCount.getOrDefault(registerSize, 0) + edge.weight
                registerSizeCount[registerSize] = registerCount
            }

            registerSizeCount.forEach { (size, count) ->
                Logger.trace { "$size: $count" }
            }
            Logger.finish()

            Logger.finish()
        }
    }

    private fun recordCircuitStats(
        name: String,
        modules: List<Module>
    ) {
        Logger.ifInfo {
            Flattener(Flattener.Mode.ALL).transform(modules).forEach { module ->
                val circuitName = module.invocation.gaplFunctionName
                val circuit = NetlistLeisersonCircuitConverter.fromModule(module.toMutableModule(), delay, maintainTiming)
                val clockPeriod = circuit.computeClockPeriod()
                val registerCount = circuit.edges.sumOf { it.weight }

                Logger.start("$name $circuitName circuit analysis", Logger.Level.INFO)
                Logger.info { "Clock Period:   $clockPeriod" }
                Logger.info { "Register Count: $registerCount" }
                Logger.finish()
            }
        }
    }

    private fun transformPiecewise(original: List<MutableModule>): List<MutableModule> {
        Logger.start("Retimer Transformer")

        val retimedModules = original.asSequence()
            .onEach { Logger.start("Retiming module ${Identifier.module(it.invocation)}") }
            .onEach { module -> recordModuleStats("Unretimed", module) }
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay, maintainTiming) }
            .onEach { graph -> recordGraphStats("Unretimed", graph) }
            .map { graph ->
                val fastSolver = FastSolver(graph)
                val finalSolver = if (minimizeRegisterCount) MinimalRegisterSolver(graph) else fastSolver
                val retiming = Retiming(
                    graph = graph,
                    graphFactory = { nodes, edges -> LeisersonCircuitGraph(graph.value, nodes, edges) }
                )
                val clockPeriod = targetClockPeriod ?: retiming.findMinimumClockPeriod(fastSolver)

                Logger.trace { "Retiming will use clock period of $clockPeriod" }
                Logger.trace { "Retiming will use ${finalSolver::class.simpleName} solver" }

                finalSolver.solveOrNull(clockPeriod) ?: throw Exception("Failed to find feasible solution")
            }
            .onEach { graph -> recordGraphStats("Retimed", graph) }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }
            .onEach { module -> recordModuleStats("Retimed", module) }
            .onEach { Logger.finish() }
            .toList()

        Logger.finish()

        return retimedModules
    }

    private fun transformAll(original: List<MutableModule>): List<MutableModule> {
        if (maintainTiming) throw Exception("Maintain timing is not supported yet")
        return HierarchicalRetimer(original).retimeAll(delay, targetClockPeriod!!)
    }

    override fun transform(original: List<Module>): List<Module> {
        val original = original.map { it.toMutableModule() }

        // Validate options
        if (mode == Mode.HIERARCHICAL) {
            if (targetClockPeriod == null) throw Exception("Must specify target clock period for hierarchical retime")
            if (!minimizeRegisterCount) throw Exception("Must specify minimize register count for hierarchical retime")
            if (maintainTiming) throw Exception("Maintain timing is not supported for hierarchical retime")
        }

        return when (mode) {
            Mode.MONOLITH -> {
                Logger.info { "Retiming in monolithic mode with target $targetClockPeriod, ${if (minimizeRegisterCount) "minimizing" else "not minimizing"} register count, and ${if (maintainTiming) "maintaining" else "not maintaining"} timing" }
                recordCircuitStats("Unretimed", original)
                transformPiecewise(original).also {
                    recordCircuitStats("Retimed", it)
                }
            }
            Mode.HIERARCHICAL -> {
                Logger.info { "Retiming in hierarchical mode with target $targetClockPeriod" }
                recordCircuitStats("Unretimed", original)
                transformAll(original).also {
                    recordCircuitStats("Retimed", it)
                }
            }
        }
    }

}