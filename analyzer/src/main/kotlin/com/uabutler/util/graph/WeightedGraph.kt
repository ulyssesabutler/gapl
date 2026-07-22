package com.uabutler.util.graph

open class WeightedGraph<N, E>(
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
) : Graph<N, E, WeightedGraph.Node<N>, WeightedGraph.Edge<N, E>, WeightedGraph<N, E>>(nodes, edges) {

    data class Node<N>(
        val weight: Int,
        override val value: N,
    ) : GraphNode<N>

    data class Edge<N, E>(
        val weight: Int,
        override val source: Node<N>,
        override val sink: Node<N>,
        override val value: E,
    ) : GraphEdge<N, E, Node<N>>

    override fun newGraph(
        nodes: Collection<Node<N>>,
        edges: Collection<Edge<N, E>>,
    ): WeightedGraph<N, E> = WeightedGraph(nodes, edges)

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

}