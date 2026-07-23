package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.Retiming
import com.uabutler.netlistir.transformer.util.retiming.solver.Solver
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph

class FastSolver<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Solver<G, N, E>(graph) {

    override fun solveOrNull(
        targetClockPeriod: Int?
    ): LeisersonCircuitGraph<G, N, E>? = Logger.run("Retiming for clock period $targetClockPeriod", Logger.Level.DEBUG) {
        if (targetClockPeriod == null) return@run graph

        val retiming = Retiming(
            graph = graph,
            graphFactory = { nodes, edges -> LeisersonCircuitGraph(graph.value, nodes, edges) }
        )

        Logger.debug { "Nodes:" }
        graph.nodes.forEach { node ->
            Logger.debug { "  $node" }
        }

        Logger.debug { "Edges:" }
        graph.edges.forEach { edge ->
            Logger.debug { "  ${edge.source.value} -> ${edge.sink.value}" }
        }

        Logger.debug { "Node Values:" }
        graph.nodes
            .map { it.value }
            .distinct()
            .sortedBy { it.toString() }
            .forEach { node ->
                Logger.debug { "  $node" }
            }

        Logger.debug { "Edge Node Values:" }
        graph.edges
            .flatMap { listOf(it.source, it.sink) }
            .distinct()
            .map { it.value }
            .sortedBy { it.toString() }
            .forEach { node ->
                Logger.debug { "  $node" }
            }

        Logger.trace { "Running ${graph.nodes.size - 1} iterations" }
        var iteration = 0
        do {
            var changed = false
            retiming.generateNewCircuit().computeCombinationalDelays().forEach { (node, delay) ->
                if (delay > targetClockPeriod) {
                    retiming.increaseNodeLag(node)
                    changed = true
                }
            }
            iteration++
        } while (changed && iteration < graph.nodes.size - 1)

        Logger.trace { "Finished after $iteration iterations" }

        val clockPeriodOfRetimedGraph = retiming.generateNewCircuit().computeClockPeriod()
        Logger.trace { "Clock period of retimed graph: $clockPeriodOfRetimedGraph" }

        return@run if (clockPeriodOfRetimedGraph <= targetClockPeriod) {
            Logger.debug { "Found feasible solution" }
            retiming.generateNewCircuit()
        } else {
            Logger.debug { "Did not find feasible solution" }
            null
        }
    }

}