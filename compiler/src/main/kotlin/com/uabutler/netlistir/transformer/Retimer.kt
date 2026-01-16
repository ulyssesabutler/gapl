package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.Logger
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

    override fun transform(original: List<Module>): List<Module> {
        Logger.start("Retimer Transformer")
        val moduleRetimability = original.groupBy { retimeModuleFilter(it) }

        val modulesToRetime = moduleRetimability[true] ?: emptyList()
        val modulesToSkip = moduleRetimability[false] ?: emptyList()

        val retimedModules = original.asSequence()
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes().sumOf { it.inputWires().size }
                    "Unretimed body wire count: $registerWires"
                }

                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .filterIsInstance<PredefinedFunctionNode>()
                        .filter { it.predefinedFunction is RegisterFunction }
                        .sumOf { it.inputWires().size }
                    "Unretimed register wire count: $registerWires"
                }
            }
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay, maintainTiming) }
            .onEach { graph ->
                Logger.debug { "Unretimed leiserson graph register count: ${graph.edges.sumOf { it.weight }}" }
                Logger.debug { "Unretimed leiserson graph clock period: ${graph.computeClockPeriod()}" }
                Logger.run("Unretimed leiserson graph edge weights") {
                    graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                        Logger.debug { "${edges.size} edges with weight $weight" }
                    }
                }
            }
            .map { graph ->
                val fastSolver = FastSolver(graph)
                val finalSolver = if (minimizeRegisterCount) MinimalRegisterSolver(graph) else fastSolver
                val clockPeriod = targetClockPeriod ?: Retiming(graph).findMinimumClockPeriod(fastSolver)

                Logger.debug { "Retiming will use clock period of $clockPeriod" }
                Logger.debug { "Retiming will use ${finalSolver::class.simpleName} solver" }

                finalSolver.solveOrNull(clockPeriod) ?: throw Exception("Failed to find feasible solution")
            }
            .onEach { graph ->
                Logger.debug { "Retimed leiserson graph register count: ${graph.edges.sumOf { it.weight }}" }
                Logger.debug { "Retimed leiserson graph clock period: ${graph.computeClockPeriod()}" }
                Logger.run("Retimed leiserson graph edge weights") {
                    graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                        Logger.debug { "${edges.size} edges with weight $weight" }
                    }
                }
            }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .sumOf { it.inputWires().size }
                    "Retimed body wire count: $registerWires"
                }

                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .filterIsInstance<PredefinedFunctionNode>()
                        .filter { it.predefinedFunction is RegisterFunction }
                        .sumOf { it.inputWires().size }
                    "Retimed register wire count: $registerWires"
                }
            }
            .toList()

        Logger.finish()

        return retimedModules + modulesToSkip
    }

}