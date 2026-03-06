package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
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
    val delay: PropagationDelay,
    val targetClockPeriod: Int?,
    val minimizeRegisterCount: Boolean,
    val maintainTiming: Boolean,
): Transformer {

    companion object {
        fun retimeModuleFilter(module: Module): Boolean {
            return module.getNodes().any {
                it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction
            }
        }
    }

    fun recordModuleStats(
        name: String,
        module: Module,
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

    fun recordGraphStats(
        name: String,
        graph: LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>,
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

    override fun transform(original: List<Module>): List<Module> {
        Logger.start("Retimer Transformer")

        /*
        val moduleRetimability = original.groupBy { retimeModuleFilter(it) }

        val modulesToRetime = moduleRetimability[true] ?: emptyList()
        val modulesToSkip = moduleRetimability[false] ?: emptyList()
         */

        val retimedModules = original.asSequence()
            .onEach { Logger.start("Retiming module ${Identifier.module(it.invocation)}") }
            .onEach { module -> recordModuleStats("Unretimed", module) }
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay, maintainTiming) }
            .onEach { graph -> recordGraphStats("Unretimed", graph) }
            .map { graph ->
                val fastSolver = FastSolver(graph)
                val finalSolver = if (minimizeRegisterCount) MinimalRegisterSolver(graph) else fastSolver
                val clockPeriod = targetClockPeriod ?: Retiming(graph).findMinimumClockPeriod(fastSolver)

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

}