package com.uabutler.util.graph.util

import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

class Retiming<G, N, E>(val graph: LeisersonCircuitGraph<G, N, E>) {

    private val nodeLag = graph.nodes.associateWith { 0 }.toMutableMap()

    abstract class Solver<G, N, E>(val graph: LeisersonCircuitGraph<G, N, E>) {
        abstract fun solveOrNull(
            targetClockPeriod: Int?,
        ): LeisersonCircuitGraph<G, N, E>?
    }

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

    fun findMinimumClockPeriod(
        solver: Solver<G, N, E>
    ): Int = Logger.run("Finding minimum clock period") {
        /*
        Logger.start("Retiming to minimize clock period")
        Logger.debug { "Initial register count: ${graph.edges.sumOf { it.weight }}" }
        Logger.debug { "Initial clock period: ${graph.computeClockPeriod()}" }

        Logger.run("Initial Edge Weights") {
            graph.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                Logger.debug { "${edges.size} edges with weight $weight" }
            }
        }
         */

        val possibleClockPeriods = Logger.run("Finding possible clock periods") {
            graph.computePossibleClockPeriods().also {
                Logger.debug { "Found ${it.size} possible clock periods" }
                Logger.debug { it.joinToString() }
            }
        }

        val cache = mutableMapOf<Int, Boolean>()
        fun attempt(clockPeriod: Int) = cache.getOrPut(clockPeriod) {
            if (solver.solveOrNull(clockPeriod) != null) {
                Logger.debug { "Clock period $clockPeriod is feasible" }
                true
            } else {
                Logger.debug { "Clock period $clockPeriod is infeasible" }
                false
            }
        }

        possibleClockPeriods.sorted().binarySearch { clockPeriod -> if (attempt(clockPeriod)) 1 else -1 }

        Logger.run("Printing Cache") {
            cache.forEach { (clockPeriod, feasible) -> Logger.debug { "$clockPeriod: $feasible" } }
        }
        return@run cache.filter { it.value }.minBy { it.key }.key
            /*
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
             */
    }

}