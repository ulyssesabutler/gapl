package com.uabutler.util.graph

class HierarchicalLeisersonCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
    // The node to attach a caller's incoming/outgoing edges to when this graph is inlined as a
    // ChildGraphNode - explicit rather than inferred from rootNodes()/leafNodes() after flattening,
    // since a function can have more than one graph-theoretic leaf (e.g. an internal value that's
    // legitimately never consumed, like an unused last-round key in a recursive round function) -
    // inferring "the" leaf from degree alone picks the wrong one whenever that happens. By
    // convention (see HierarchicalNetlistLeisersonCircuitConverter.fromModule) these are always the
    // graph's own super-input/super-output VirtualNode, but nothing here requires that.
    val rootAttachment: Node<N>,
    val leafAttachment: Node<N>,
) : Graph<N, E,
    HierarchicalLeisersonCircuitGraph.Node<N>,
    HierarchicalLeisersonCircuitGraph.Edge<N, E>,
    HierarchicalLeisersonCircuitGraph<G, N, E>,
>(nodes, edges) {

    sealed class Node<N> : GraphNode<N>

    data class LeafNode<N>(override val value: N, val weight: Int) : Node<N>()
    data class ChildGraphNode<G, N, E>(override val value: N, val childGraph: HierarchicalLeisersonCircuitGraph<G, N, E>) : Node<N>()
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
    ): HierarchicalLeisersonCircuitGraph<G, N, E> = HierarchicalLeisersonCircuitGraph(value, nodes, edges, rootAttachment, leafAttachment)

    fun childNodes(): List<ChildGraphNode<G, N, E>> = nodes.filterIsInstance<ChildGraphNode<G, N, E>>()

    fun childGraphs(): List<HierarchicalLeisersonCircuitGraph<G, N, E>> = childNodes().map { it.childGraph }

    data class Flattened<N, E>(
        val graph: WeightedGraph<N, E>,
        val rootAttachment: WeightedGraph.Node<N>,
        val leafAttachment: WeightedGraph.Node<N>,
    )

    /**
     * Recursively inlines every [ChildGraphNode] into its containing graph, producing a single flat
     * [LeisersonCircuitGraph]. A [ChildGraphNode] is replaced by its (recursively flattened) child graph's nodes
     * and edges; edges that pointed into/out of the [ChildGraphNode] are redirected to the child graph's
     * [rootAttachment] / [leafAttachment].
     *
     * [LeisersonCircuitGraph]'s constructor is itself a zero-weight-cycle check, so this throws immediately if
     * the flattened result contains one - fine for callers that treat a cycle as unrecoverable (retiming), but
     * unusable for callers that want to inspect *which* nodes are involved. Use [flattenToWeightedGraph] for that.
     */
    fun flatten(virtualNodeWeight: (VirtualNode<N>) -> Int = { 0 }): LeisersonCircuitGraph<G, N, E> =
        flattenToWeightedGraph(virtualNodeWeight).graph.let { LeisersonCircuitGraph(value, it.nodes, it.edges) }

    /**
     * Same recursive inlining as [flatten], but returns a plain [WeightedGraph] - no cycle validation, so a
     * combinational loop in the input doesn't throw here. Callers that need to know whether/where a cycle exists
     * (rather than treating one as an unrecoverable error) should inspect the result themselves, e.g. via
     * [Graph.subgraph] + [Graph.stronglyConnectedComponentsTarjan].
     */
    fun flattenToWeightedGraph(virtualNodeWeight: (VirtualNode<N>) -> Int = { 0 }): Flattened<N, E> {
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
            val flatChild = node.childGraph.flattenToWeightedGraph(virtualNodeWeight)
            allFlatNodes.addAll(flatChild.graph.nodes)
            allFlatEdges.addAll(flatChild.graph.edges)

            childInputAttachment[node] = flatChild.rootAttachment
            childOutputAttachment[node] = flatChild.leafAttachment
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

        return Flattened(
            graph = WeightedGraph(allFlatNodes, allFlatEdges),
            rootAttachment = resolveSink(rootAttachment),
            leafAttachment = resolveSource(leafAttachment),
        )
    }
}
