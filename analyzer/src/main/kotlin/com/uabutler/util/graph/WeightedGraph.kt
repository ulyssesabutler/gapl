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

        // Bellman-Ford's correctness (and termination in nodes.size - 1 rounds) assumes no
        // negative-weight cycle is reachable from root. That assumption doesn't hold for every
        // caller here - e.g. HierarchicalMinimalRegisterSolver's per-child "expansion" edges are
        // deliberately negative-weighted (see MinimalRegisterSolver.computeUpperRetimingUpperBound's
        // comment), and a real register-protected feedback loop crossing such an edge can produce a
        // net-negative cycle. Undetected, that silently corrupts distances (even a node's distance
        // to *itself*, which must always be `zero`, can get overwritten by a lower-but-meaningless
        // value from looping the cycle) into arbitrary, iteration-count-dependent numbers - not "a
        // large but real" distance, but a genuinely undefined one, since a reachable negative cycle
        // means the true shortest-walk distance is unbounded below. Detect every node still
        // reachable from such a cycle (one more relaxation pass finds directly-corrupted nodes;
        // repeating until fixpoint propagates that to everything reachable from them) and drop them
        // from the result entirely, rather than handing a caller a wrong number it can't tell apart
        // from a correct one.
        val corrupted = mutableSetOf<Node<N>>()
        var corruptionChanged = true
        while (corruptionChanged) {
            corruptionChanged = false
            edges.forEach { edge ->
                val distanceToEdgeSource = distanceFromRoot[edge.source]
                if (distanceToEdgeSource != null && edge.source !in corrupted) {
                    val candidateDistance = weightAddition(distanceToEdgeSource, edgeWeight(edge))
                    val distanceToEdgeSink = distanceFromRoot[edge.sink]
                    val improves = distanceToEdgeSink == null || weightComparator.compare(candidateDistance, distanceToEdgeSink) < 0
                    if (improves && edge.sink !in corrupted) {
                        corrupted.add(edge.sink)
                        corruptionChanged = true
                    }
                } else if (edge.source in corrupted && edge.sink in distanceFromRoot && edge.sink !in corrupted) {
                    corrupted.add(edge.sink)
                    corruptionChanged = true
                }
            }
        }

        return distanceFromRoot.filterKeys { it !in corrupted }
    }

}