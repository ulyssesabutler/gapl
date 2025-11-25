package com.uabutler.util.graph

import com.uabutler.util.Logger

class Retiming<G, N, E>(val graph: LeisersonCircuitGraph<G, N, E>) {

    companion object {

        private fun <G, N, E> retimeForClockPeriod(graph: LeisersonCircuitGraph<G, N, E>, clockPeriod: Int): Retiming<G, N, E>? {
            Logger.start("Retiming for clock period $clockPeriod")
            val retiming = Retiming(graph)

            repeat(graph.nodes.size - 1) {
                Logger.debug("Iteration $it")
                retiming.generateNewCircuit().computeCombinationalDelays().forEach { (node, delay) ->
                    if (delay > clockPeriod) retiming.increaseNodeLag(node)
                }
            }

            val clockPeriodOfRetimedGraph = retiming.generateNewCircuit().computeClockPeriod()
            Logger.debug("Clock period of retimed graph: $clockPeriodOfRetimedGraph")

            return (if (clockPeriodOfRetimedGraph <= clockPeriod) retiming else null).also { Logger.finish() }
        }

        fun <G, N, E> minimizeClockPeriod(graph: LeisersonCircuitGraph<G, N, E>): LeisersonCircuitGraph<G,N, E> {
            Logger.start("Retiming")
            Logger.debug("Initial register count: ${graph.edges.sumOf { it.weight }}")
            Logger.debug("Initial clock period: ${graph.computeClockPeriod()}")

            Logger.start("Initial edge weights")
            graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                Logger.debug("${edges.size} edges with weight $weight")
            }
            Logger.finish()

            val possibleClockPeriods = graph.computePossibleClockPeriods()
            Logger.debug("Possible clock periods: $possibleClockPeriods")

            val cache = mutableMapOf<Int, Retiming<G, N, E>?>()
            fun attempt(clockPeriod: Int) = cache.getOrPut(clockPeriod) { retimeForClockPeriod(graph, clockPeriod) }

            possibleClockPeriods.sorted().binarySearch { clockPeriod ->
                if (attempt(clockPeriod) == null) -1 else 1
            }

            return cache
                .filter { it.value != null }
                .minBy { it.key }
                .value!!.generateNewCircuit()
                .also {
                    Logger.debug("Final register count: ${it.edges.sumOf { it.weight }}")
                    Logger.debug("Final clock period: ${it.computeClockPeriod()}")

                    Logger.start("Final edge weights")
                    it.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                        Logger.debug("${edges.size} edges with weight $weight")
                    }
                    Logger.finish()

                    Logger.finish()
                }
        }

    }

    private val nodeLag = graph.nodes.associateWith { 0 }.toMutableMap()

    fun setNodeLag(node: WeightedGraph.Node<N>, lag: Int) = nodeLag.put(node, lag)

    fun getNodeLag(node: WeightedGraph.Node<N>) = nodeLag[node]!!

    fun getEdgeRegisterCount(edge: WeightedGraph.Edge<N, E>): Int = edge.weight + getNodeLag(edge.sink) - getNodeLag(edge.source)

    fun increaseNodeLag(node: WeightedGraph.Node<N>, increase: Int = 1) = setNodeLag(node, getNodeLag(node) + increase)

    fun generateNewCircuit(): LeisersonCircuitGraph<G, N, E> {
        return try {
            LeisersonCircuitGraph(
                value = graph.value,
                nodes = graph.nodes,
                edges = graph.edges.map { edge -> edge.copy(weight = getEdgeRegisterCount(edge)) },
            )
        } catch (e: IllegalArgumentException) {
            throw Exception("Failed to generate graph: Illegal retiming", e)
        }
    }

}