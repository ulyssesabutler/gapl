package com.uabutler.util.graph

import com.uabutler.netlistir.netlist.Node

class Retiming<N, E>(val graph: LeisersonCircuitGraph<N, E>) {

    companion object {

        fun <N, E> retimeForClockPeriod(graph: LeisersonCircuitGraph<N, E>, clockPeriod: Int): Retiming<N, E>? {
            val retiming = Retiming(graph)

            repeat(graph.nodes.size - 1) {
                retiming.generateNewCircuit().computeCombinationalDelays().forEach { (node, delay) ->
                    if (delay > clockPeriod) retiming.increaseNodeLag(node)
                }
            }

            val clockPeriodOfRetimedGraph = retiming.generateNewCircuit().computeClockPeriod()

            return if (clockPeriodOfRetimedGraph <= clockPeriod) retiming else null
        }

        fun <N, E> minimizeClockPeriod(graph: LeisersonCircuitGraph<N, E>): LeisersonCircuitGraph<N, E> {
            val possibleClockPeriods = graph.computePossibleClockPeriods()

            val cache = mutableMapOf<Int, Retiming<N, E>?>()
            fun attempt(clockPeriod: Int) = cache.getOrPut(clockPeriod) { retimeForClockPeriod(graph, clockPeriod) }

            possibleClockPeriods.sorted().binarySearch { clockPeriod ->
                if (attempt(clockPeriod) == null) -1 else 1
            }

            return cache
                .filter { it.value != null }
                .minBy { it.key }
                .value!!.generateNewCircuit()
        }

    }

    val nodeLag = graph.nodes.associateWith { 0 }.toMutableMap()

    fun setNodeLag(node: WeightedGraph.Node<N>, lag: Int) = nodeLag.put(node, lag)

    fun getNodeLag(node: WeightedGraph.Node<N>) = nodeLag[node]!!

    fun getEdgeRegisterCount(edge: WeightedGraph.Edge<N, E>): Int = edge.weight + getNodeLag(edge.sink) - getNodeLag(edge.source)

    fun increaseNodeLag(node: WeightedGraph.Node<N>, increase: Int = 1) = setNodeLag(node, getNodeLag(node) + increase)

    fun generateNewCircuit(): LeisersonCircuitGraph<N, E> {
        return LeisersonCircuitGraph(
            module = graph.module,
            nodes = graph.nodes,
            edges = graph.edges.map { edge -> edge.copy(weight = getEdgeRegisterCount(edge)) },
        )
    }

}