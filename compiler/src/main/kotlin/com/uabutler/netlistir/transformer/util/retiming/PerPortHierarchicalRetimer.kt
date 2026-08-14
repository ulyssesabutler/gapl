package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.RetimingInfeasibleException
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualBodyNode
import com.uabutler.netlistir.transformer.util.retiming.solver.MinimalRegisterSolver
import com.uabutler.netlistir.transformer.util.retiming.solver.PerPortHierarchicalMinimalRegisterSolver
import com.uabutler.netlistir.util.graph.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.netlistir.util.graph.PortGraph
import com.uabutler.netlistir.util.graph.PortHierarchicalNetlistConverter
import com.uabutler.util.Logger
import com.uabutler.util.PropagationDelay

/**
 * The per-port counterpart of [HierarchicalRetimer]: builds [PortHierarchicalNetlistConverter]
 * graphs, where each call site is one node per port, and hands them to
 * [PerPortHierarchicalMinimalRegisterSolver].
 */
class PerPortHierarchicalRetimer(
    val modules: Collection<MutableModule>,
) {

    fun retimeAll(propagationDelay: PropagationDelay, targetClockPeriod: Int?): List<MutableModule> {
        val graphs = PortHierarchicalNetlistConverter.fromModules(modules, propagationDelay)

        var expansionCounter = 0
        val solver = PerPortHierarchicalMinimalRegisterSolver(
            graphs = graphs,
            expansionNodeFactory = {
                VirtualBodyNode(
                    identifier = "port-expansion-${expansionCounter++}",
                    parentModule = modules.first(),
                ) as Node
            },
            expansionEdgeValueFactory = { emptyList<NonRegisterConnection>() },
            edgeSourceBits = MinimalRegisterSolver.Companion::netlistEdgeSourceBits,
        )

        val clockPeriod = targetClockPeriod ?: findMinimumClockPeriod(solver, solver.problem)
        val retimedProblem = solver.solveOrNull(clockPeriod)
            ?: throw RetimingInfeasibleException(
                "No feasible per-port hierarchical retiming found for clock period $clockPeriod"
            )

        printStats(graphs, solver)

        return PortHierarchicalNetlistConverter.toModules(retimedProblem.graphs)
    }

    private fun printStats(
        graphs: List<PortGraph>,
        solver: PerPortHierarchicalMinimalRegisterSolver<MutableModule, Node, Collection<NonRegisterConnection>>,
    ) {
        Logger.ifInfo {
            graphs.forEach { graph ->
                val summary = solver.summaryFromLastSolve(graph) ?: return@forEach
                Logger.start("${graph.value.invocation.gaplFunctionName} retiming analysis", Logger.Level.INFO)
                Logger.info { "Clock Period:   ${summary.clockPeriod}" }
                Logger.info { "Register Count: ${summary.ownRegisterCount}" }
                graph.inputPorts.forEach { port ->
                    Logger.info { "  in  ${port.value.name()}: lag=${summary.portLags[port]} delay=${summary.inputDelays[port]}" }
                }
                graph.outputPorts.forEach { port ->
                    Logger.info { "  out ${port.value.name()}: lag=${summary.portLags[port]} delay=${summary.outputDelays[port]}" }
                }
                Logger.finish()
            }
        }
    }
}
