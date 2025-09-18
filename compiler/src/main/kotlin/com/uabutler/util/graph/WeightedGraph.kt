package com.uabutler.util.graph

open class WeightedGraph<N, E>(
    val nodes: List<Node<N>>,
    val edges: List<Edge<N, E>>,
) {

    private val adjacencyList: Map<Node<N>, List<Edge<N, E>>> = edges.groupBy { it.source }


    data class Node<N>(
        val weight: Int,
        val value: N,
    )

    data class Edge<N, E>(
        val weight: Int,
        val source: Node<N>,
        val sink: Node<N>,
        val value: E,
    )

    fun <T> shortestPathsFromNode(
        root: Node<N>,
        edgeWeight: (Edge<N, E>) -> T,
        weightComparator: Comparator<T>,
        weightAddition: (T, T) -> T,
        zero: T,
    ): Map<Node<N>, T> {
        // Bellman-Ford Algorithm
        val distanceFromRoot = mutableMapOf(root to zero)

        repeat(nodes.size - 1) {
            var changed = false

            edges.forEach { edge ->
                val distanceToEdgeSource = distanceFromRoot[edge.source]

                if (distanceToEdgeSource != null) {
                    val candidateDistance = weightAddition(distanceToEdgeSource, edgeWeight(edge))
                    val distanceToEdgeSink = distanceFromRoot[edge.sink]
                    if (distanceToEdgeSink == null || weightComparator.compare(candidateDistance, distanceToEdgeSink) < 0) {
                        distanceFromRoot[edge.sink] = candidateDistance
                        changed = true
                    }
                }
            }

            // Early exit if no changes in this pass
            if (!changed) return distanceFromRoot
        }

        return distanceFromRoot
    }

    fun topologicalSort(): List<Node<N>> {
        // Kahn's algorithm
        val inDegree = edges.groupBy { it.sink }.mapValues { (_, edges) -> edges.size }.toMutableMap()

        val currentStartNodes = inDegree.filterValues { it == 0 }.keys.toMutableList()

        return buildList {
            while (currentStartNodes.isNotEmpty()) {
                val currentNode = currentStartNodes.removeLast()

                add(currentNode)

                adjacencyList[currentNode]?.forEach { edge ->
                    inDegree[edge.sink] = (inDegree[edge.sink] ?: 0) - 1
                    if (inDegree[edge.sink] == 0) currentStartNodes.add(edge.sink)
                }
            }
        }
    }

    fun subgraph(
        nodeFilter: (Node<N>) -> Boolean = { true },
        edgeFilter: (Edge<N, E>) -> Boolean = { true },
    ): WeightedGraph<N, E> {
        return WeightedGraph(
            nodes = nodes.filter { nodeFilter(it) },
            edges = edges.filter { edgeFilter(it) }.filter { nodeFilter(it.source) && nodeFilter(it.sink) },
        )
    }

}