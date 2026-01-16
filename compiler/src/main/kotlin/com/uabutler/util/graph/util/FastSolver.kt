package com.uabutler.util.graph.util

import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph

class FastSolver<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming.Solver<G, N, E>(graph) {

    override fun solveOrNull(
        targetClockPeriod: Int?
    ): LeisersonCircuitGraph<G, N, E>? = Logger.run("Retiming for clock period $targetClockPeriod") {
        if (targetClockPeriod == null) return@run graph

        val retiming = Retiming(graph)

        Logger.debug { "Running ${graph.nodes.size - 1} iterations" }
        var iteration = 0
        do {
            Logger.debug { "Iteration $iteration" }
            var changed = false
            retiming.generateNewCircuit().computeCombinationalDelays().forEach { (node, delay) ->
                if (delay > targetClockPeriod) {
                    retiming.increaseNodeLag(node)
                    changed = true
                }
            }
            iteration++
        } while (changed && iteration < graph.nodes.size - 1)

        val clockPeriodOfRetimedGraph = retiming.generateNewCircuit().computeClockPeriod()
        Logger.debug { "Clock period of retimed graph: $clockPeriodOfRetimedGraph" }

        return@run if (clockPeriodOfRetimedGraph <= targetClockPeriod) {
            Logger.debug { "Found feasible solution" }
            retiming.generateNewCircuit()
        } else {
            Logger.debug { "Did not find feasible solution" }
            null
        }
    }

}