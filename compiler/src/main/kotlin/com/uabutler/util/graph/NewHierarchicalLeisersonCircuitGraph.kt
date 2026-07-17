package com.uabutler.util.graph

class NewHierarchicalLeisersonCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
) : Graph<N, E,
    NewHierarchicalLeisersonCircuitGraph.Node<N>,
    NewHierarchicalLeisersonCircuitGraph.Edge<N, E>,
    NewHierarchicalLeisersonCircuitGraph<G, N, E>,
>(nodes, edges) {

    sealed class Node<N> : GraphNode<N>

    data class LeafNode<N>(override val value: N, val weight: Int) : Node<N>()
    data class ChildGraphNode<G, N, E>(override val value: N, val childGraph: NewHierarchicalLeisersonCircuitGraph<G, N, E>) : Node<N>()
    data class VirtualNode<N>(override val value: N) : Node<N>()

    data class Edge<N, E>(
        override val source: Node<N>,
        override val sink: Node<N>,
        override val value: E,
        val weight: Int,
    ) : GraphEdge<N, E, Node<N>>

    override fun newGraph(
        nodes: Collection<Node<N>>,
        edges: Collection<Edge<N, E>>,
    ): NewHierarchicalLeisersonCircuitGraph<G, N, E> = NewHierarchicalLeisersonCircuitGraph(value, nodes, edges)

    fun childNodes(): List<ChildGraphNode<G, N, E>> = nodes.filterIsInstance<ChildGraphNode<G, N, E>>()

    fun childGraphs(): List<NewHierarchicalLeisersonCircuitGraph<G, N, E>> = childNodes().map { it.childGraph }
}
