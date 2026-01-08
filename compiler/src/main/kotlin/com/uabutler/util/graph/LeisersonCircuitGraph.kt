package com.uabutler.util.graph

import com.uabutler.util.Logger

open class LeisersonCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
): WeightedGraph<N, E>(nodes, edges) {

    init {
        if (nodes.any { it.weight < 0 }) throw IllegalArgumentException("Node weights must be non-negative")
        if (edges.any { it.weight < 0 }) throw IllegalArgumentException("Edge weights must be non-negative")

        try {
            subgraph(edgeFilter = { it.weight == 0 }).topologicalSort()
        } catch (_: Exception) {
            throw IllegalArgumentException("Graph cannot contain zero-weight cycles")
        }
    }

    fun computeCombinationalDelays(): Map<Node<N>, Int> {
        val combinationalSubgraph = subgraph(edgeFilter = { it.weight == 0 })
        val incomingNodes = combinationalSubgraph.edges.groupBy { it.sink }.mapValues { (_, edges) -> edges.map { it.source } }
        val combinationalDelay = mutableMapOf<Node<N>, Int>()

        combinationalSubgraph.topologicalSort().forEach { node ->
            val incomingCombinationalDelay = incomingNodes[node]?.maxOfOrNull { combinationalDelay[it] ?: 0 } ?: 0
            combinationalDelay[node] = node.weight + incomingCombinationalDelay
        }

        return combinationalDelay
    }

    fun computeClockPeriod() = computeCombinationalDelays().values.max()

    data class FastestConnection<N>(
        val source: Node<N>,
        val sink: Node<N>,
        val delay: Int,
        val registerCount: Int,
    )

    fun findFastestConnectionsFromNode(sourceNode: Node<N>): List<FastestConnection<N>> {
        return shortestPathsFromNode(
            root = sourceNode,
            edgeWeight = { it.weight to -it.source.weight },
            weightComparator = compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
            weightAddition = { a, b -> a.first + b.first to a.second + b.second },
            zero = 0 to 0,
        ).map { (sinkNode, distance) ->
            FastestConnection(sourceNode, sinkNode, sinkNode.weight - distance.second, distance.first)
        }
    }

    fun computePossibleClockPeriods() = Logger.run("Computing possible clock periods") {
        nodes.asSequence()
            .map { findFastestConnectionsFromNode(it) }
            .flatMap { it.asSequence() }
            .map { it.delay }
            .toSet()
            .also { Logger.debug { "Found ${it.size} possible clock periods" } }
    }

}
