package com.uabutler.util.graph.util

import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

class ClockPeriodMinimizer<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming<G, N, E>(graph) {

    companion object {

        private fun <G, N, E> retimeForClockPeriod(graph: LeisersonCircuitGraph<G, N, E>, clockPeriod: Int): ClockPeriodMinimizer<G, N, E>? {
            Logger.start("Retiming for clock period $clockPeriod")
            val retiming = ClockPeriodMinimizer(graph)

            Logger.debug { "Running ${graph.nodes.size - 1} iterations" }
            repeat(graph.nodes.size - 1) {
                retiming.generateNewCircuit().computeCombinationalDelays().forEach { (node, delay) ->
                    if (delay > clockPeriod) retiming.increaseNodeLag(node)
                }
            }

            val clockPeriodOfRetimedGraph = retiming.generateNewCircuit().computeClockPeriod()
            Logger.debug { "Clock period of retimed graph: $clockPeriodOfRetimedGraph" }

            return (if (clockPeriodOfRetimedGraph <= clockPeriod) retiming else null).also {
                Logger.debug { "Retiming ${if (it == null) "was not" else "was"} successful" }
                Logger.finish()
            }
        }

        fun <G, N, E> minimizeClockPeriod(graph: LeisersonCircuitGraph<G, N, E>): LeisersonCircuitGraph<G, N, E> {
            Logger.start("Retiming")
            Logger.debug { "Initial register count: ${graph.edges.sumOf { it.weight }}" }
            Logger.debug { "Initial clock period: ${graph.computeClockPeriod()}" }

            Logger.run("Initial Edge Weights") {
                graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                    Logger.debug { "${edges.size} edges with weight $weight" }
                }
            }

            val possibleClockPeriods = graph.computePossibleClockPeriods()

            val cache = mutableMapOf<Int, ClockPeriodMinimizer<G, N, E>?>()
            fun attempt(clockPeriod: Int) = cache.getOrPut(clockPeriod) { retimeForClockPeriod(graph, clockPeriod) }

            possibleClockPeriods.sorted().binarySearch { clockPeriod ->
                if (attempt(clockPeriod) == null) -1 else 1
            }

            return cache
                .filter { it.value != null }
                .minBy { it.key }
                .value!!.generateNewCircuit()
                .also {
                    Logger.debug { "Final register count: ${it.edges.sumOf { it.weight }}" }
                    Logger.debug { "Final clock period: ${it.computeClockPeriod()}" }

                    Logger.run("Final edge weights") {
                        it.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                            Logger.debug { "${edges.size} edges with weight $weight" }
                        }
                    }

                    Logger.finish()
                }
        }

    }

}