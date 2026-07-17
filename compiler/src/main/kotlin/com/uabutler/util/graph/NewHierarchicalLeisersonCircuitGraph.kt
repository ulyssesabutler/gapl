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

    /**
     * Recursively inlines every [ChildGraphNode] into its containing graph, producing a single flat
     * [LeisersonCircuitGraph]. A [ChildGraphNode] is replaced by its (recursively flattened) child graph's nodes
     * and edges; edges that pointed into/out of the [ChildGraphNode] are redirected to the child graph's unique
     * root node / leaf node (its "super input" / "super output" [VirtualNode], per convention).
     */
    fun flatten(virtualNodeWeight: (VirtualNode<N>) -> Int = { 0 }): LeisersonCircuitGraph<G, N, E> {
        val directFlatNode = mutableMapOf<Node<N>, WeightedGraph.Node<N>>()
        val childInputAttachment = mutableMapOf<Node<N>, WeightedGraph.Node<N>>()
        val childOutputAttachment = mutableMapOf<Node<N>, WeightedGraph.Node<N>>()

        val allFlatNodes = mutableListOf<WeightedGraph.Node<N>>()
        val allFlatEdges = mutableListOf<WeightedGraph.Edge<N, E>>()

        nodes.forEach { node ->
            when (node) {
                is LeafNode -> {
                    val flatNode = WeightedGraph.Node(node.weight, node.value)
                    directFlatNode[node] = flatNode
                    allFlatNodes.add(flatNode)
                }
                is VirtualNode -> {
                    val flatNode = WeightedGraph.Node(virtualNodeWeight(node), node.value)
                    directFlatNode[node] = flatNode
                    allFlatNodes.add(flatNode)
                }
                is ChildGraphNode<*, *, *> -> { /* handled below, after this loop, with proper generics */ }
            }
        }

        childNodes().forEach { node ->
            val flatChildGraph = node.childGraph.flatten(virtualNodeWeight)
            allFlatNodes.addAll(flatChildGraph.nodes)
            allFlatEdges.addAll(flatChildGraph.edges)

            val roots = flatChildGraph.rootNodes()
            val leaves = flatChildGraph.leafNodes()
            require(roots.size == 1) {
                "Child graph for node ${node.value} must have exactly one root node, found ${roots.size}"
            }
            require(leaves.size == 1) {
                "Child graph for node ${node.value} must have exactly one leaf node, found ${leaves.size}"
            }

            childInputAttachment[node] = roots.single()
            childOutputAttachment[node] = leaves.single()
        }

        fun resolveSource(node: Node<N>) = directFlatNode[node] ?: childOutputAttachment.getValue(node)
        fun resolveSink(node: Node<N>) = directFlatNode[node] ?: childInputAttachment.getValue(node)

        edges.forEach { edge ->
            allFlatEdges.add(
                WeightedGraph.Edge(
                    weight = edge.weight,
                    source = resolveSource(edge.source),
                    sink = resolveSink(edge.sink),
                    value = edge.value,
                )
            )
        }

        return LeisersonCircuitGraph(value, allFlatNodes, allFlatEdges)
    }
}
