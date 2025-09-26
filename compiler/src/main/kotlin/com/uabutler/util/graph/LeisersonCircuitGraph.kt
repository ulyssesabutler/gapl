package com.uabutler.util.graph

open class LeisersonCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
): WeightedGraph<N, E>(nodes, edges) {

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

    fun computePossibleClockPeriods(): Collection<Int> {
        return nodes.asSequence()
            .map { node ->
                shortestPathsFromNode(
                    root = node,
                    edgeWeight = { it.weight to -it.source.weight },
                    weightComparator = compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
                    weightAddition = { a, b -> a.first + b.first to a.second + b.second },
                    zero = 0 to 0,
                )
            }
            .flatMap { it.asSequence() }
            .map { (node, distance) -> node.weight - distance.second }
            .toSet()
    }

    fun retimed() = Retiming.minimizeClockPeriod(this)

}
