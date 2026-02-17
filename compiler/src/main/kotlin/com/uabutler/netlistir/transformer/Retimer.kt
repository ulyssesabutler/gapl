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
        Logger.debug {
            val registerWires = module.getBodyNodes().sumOf { it.inputWires().size }
            "$name body wire count: $registerWires"
        }

        Logger.debug {
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
        Logger.debug { "$name leiserson graph register count: ${graph.edges.sumOf { it.weight }}" }
        Logger.debug { "$name leiserson graph clock period: ${graph.computeClockPeriod()}" }
        Logger.run("$name leiserson graph edge weights") {
            graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                Logger.debug { "${edges.size} edges with weight $weight" }
            }
        }

        Logger.ifDebug {
            Logger.start("$name graph path analysis")
            val inputNodes = graph.nodes.filter { it.value is InputNode }.toList()
            val outputNodes = graph.nodes.filter { it.value is OutputNode }.toSet()

            Logger.start("Shortest combinational delays")
            inputNodes.forEach { inputNode ->
                val fastestConnectionsToOutputs = graph.findFastestConnectionsFromNode(inputNode)
                    .filter { it.sink in outputNodes }

                fastestConnectionsToOutputs.forEach { connection ->
                    Logger.debug { "${connection.source.value.name()} -> ${connection.sink.value.name()}: ${connection.registerCount}" }
                }
            }
            Logger.finish()

            Logger.start("All combinational delays")
            inputNodes.forEach { inputNode ->
                val fastestConnectionsToOutputs = graph.registerDelaysFrom(inputNode)
                    .filter { it.key in outputNodes }

                fastestConnectionsToOutputs.forEach { connection ->
                    Logger.debug { "${inputNode.value.name()} -> ${connection.key.value.name()}: ${connection.value}" }
                }
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
            .onEach { module -> recordModuleStats("Unretimed", module) }
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay, maintainTiming) }
            .onEach { graph -> recordGraphStats("Unretimed", graph) }
            .map { graph ->
                val fastSolver = FastSolver(graph)
                val finalSolver = if (minimizeRegisterCount) MinimalRegisterSolver(graph) else fastSolver
                val clockPeriod = targetClockPeriod ?: Retiming(graph).findMinimumClockPeriod(fastSolver)

                Logger.debug { "Retiming will use clock period of $clockPeriod" }
                Logger.debug { "Retiming will use ${finalSolver::class.simpleName} solver" }

                finalSolver.solveOrNull(clockPeriod) ?: throw Exception("Failed to find feasible solution")
            }
            .onEach { graph -> recordGraphStats("Retimed", graph) }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }
            .onEach { module -> recordModuleStats("Retimed", module) }
            .toList()

        Logger.finish()

        return retimedModules
    }

}