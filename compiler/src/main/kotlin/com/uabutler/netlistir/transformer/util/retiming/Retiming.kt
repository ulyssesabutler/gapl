package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.netlistir.transformer.util.retiming.solver.Solver
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

class Retiming<G, N, E>(
    val graph: LeisersonCircuitGraph<G, N, E>,
    private val graphFactory: (Collection<WeightedGraph.Node<N>>, Collection<WeightedGraph.Edge<N, E>>) -> LeisersonCircuitGraph<G, N, E>
) {

    private val nodeLag = graph.nodes.associateWith { 0 }.toMutableMap()

    fun setNodeLag(node: WeightedGraph.Node<N>, lag: Int) = nodeLag.put(node, lag)

    fun getNodeLag(node: WeightedGraph.Node<N>) = try {
        nodeLag[node]!!
    } catch (e: NullPointerException) {
        nodeLag.keys.forEach { Logger.debug { "$it: ${nodeLag[it]}" } }
        throw Exception("Node $node is not in the graph", e)
    }

    fun getEdgeRegisterCount(edge: WeightedGraph.Edge<N, E>): Int = edge.weight + getNodeLag(edge.sink) - getNodeLag(edge.source)

    fun increaseNodeLag(node: WeightedGraph.Node<N>, increase: Int = 1) = setNodeLag(node, getNodeLag(node) + increase)

    fun generateNewCircuit(): LeisersonCircuitGraph<G, N, E> {
        return try {
            graphFactory(
                graph.nodes,
                graph.edges.map { edge -> edge.copy(weight = getEdgeRegisterCount(edge)) },
            )
        } catch (e: IllegalArgumentException) {
            throw Exception("Failed to generate graph: Illegal retiming", e)
        }
    }

    fun findMinimumClockPeriod(
        solver: Solver<G, N, E>
    ): Int = Logger.run("Finding minimum clock period") {
        val possibleClockPeriods = Logger.run("Finding possible clock periods") {
            graph.computePossibleClockPeriods().also {
                Logger.debug { "Found ${it.size} possible clock periods" }
                Logger.debug { it.joinToString() }
            }
        }

        val cache = mutableMapOf<Int, Boolean>()
        fun attempt(clockPeriod: Int): Boolean {
            if (cache.containsKey(clockPeriod)) {
                Logger.debug { "Clock period $clockPeriod was already checked" }
                return cache[clockPeriod]!!
            }

            val solvedGraph = solver.solveOrNull(clockPeriod)

            if (solvedGraph != null) {
                Logger.debug { "Clock period $clockPeriod is feasible" }

                possibleClockPeriods
                    .filter { it >= solvedGraph.computeClockPeriod() }
                    .forEach { cache[it] = true }

                return true
            } else {
                Logger.debug { "Clock period $clockPeriod is infeasible" }
                return false
            }
        }

        possibleClockPeriods.sorted().binarySearch { clockPeriod -> if (attempt(clockPeriod)) 1 else -1 }

        return@run cache.filter { it.value }.minBy { it.key }.key
    }

}