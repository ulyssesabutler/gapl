package com.uabutler.util.graph

import java.util.IdentityHashMap

/**
 * A hierarchical retiming graph in which a child instance is represented by **one node per port**
 * rather than by a single node.
 *
 * This is the sibling of [HierarchicalLeisersonCircuitGraph], not a replacement: that type collapses
 * every call site to one [HierarchicalLeisersonCircuitGraph.ChildGraphNode], which necessarily
 * merges all of a child's input ports into one graph position and all of its output ports into
 * another. That merge is what forces every input-to-output path through a module to share a single
 * retiming difference, and it is why a parent-side feedback loop through a child can be infeasible
 * at every clock period while the same circuit retimes fine monolithically. See
 * `brainstorming/per-port-hierarchical-retiming.md`.
 *
 * There are no super-source/super-sink nodes here. A module's own boundary is [inputPorts] and
 * [outputPorts] - real nodes that carry real delay - and anything that needs the ports to share a
 * lag (only the top of the hierarchy does) says so with an explicit constraint rather than by
 * routing them through a synthetic node.
 */
class PortHierarchicalCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
    val inputPorts: List<LeafNode<N>>,
    val outputPorts: List<LeafNode<N>>,
) : Graph<N, E,
    PortHierarchicalCircuitGraph.Node<N>,
    PortHierarchicalCircuitGraph.Edge<N, E>,
    PortHierarchicalCircuitGraph<G, N, E>,
>(nodes, edges) {

    sealed class Node<N> : GraphNode<N>

    /** Anything the module owns outright, including its own port nodes. */
    data class LeafNode<N>(override val value: N, val weight: Int) : Node<N>()

    /**
     * One port of one child instance.
     *
     * [value] identifies the *call site* (for a netlist graph, the `ModuleInvocationNode`), so two
     * instances of the same function never merge. [port] is the child graph's own [LeafNode] for
     * that port, so the several nodes belonging to one instance stay distinct from each other.
     */
    data class ChildPortNode<G, N, E>(
        override val value: N,
        val childGraph: PortHierarchicalCircuitGraph<G, N, E>,
        val port: LeafNode<N>,
        val isInput: Boolean,
    ) : Node<N>()

    data class Edge<N, E>(
        override val source: Node<N>,
        override val sink: Node<N>,
        override val value: E,
        val weight: Int,
    ) : GraphEdge<N, E, Node<N>>

    override fun newGraph(
        nodes: Collection<Node<N>>,
        edges: Collection<Edge<N, E>>,
    ): PortHierarchicalCircuitGraph<G, N, E> =
        PortHierarchicalCircuitGraph(value, nodes, edges, inputPorts, outputPorts)

    fun childPortNodes(): List<ChildPortNode<G, N, E>> = nodes.filterIsInstance<ChildPortNode<G, N, E>>()

    /** One call site: its identity, the graph it instantiates, and its per-port nodes. */
    data class ChildInstance<G, N, E>(
        val value: N,
        val childGraph: PortHierarchicalCircuitGraph<G, N, E>,
        val ports: List<ChildPortNode<G, N, E>>,
    )

    /**
     * The child instances in this graph, each with all of its port nodes.
     *
     * Grouped by *identity* of the call-site value, matching [Graph]'s own bookkeeping: two distinct
     * call sites of the same function can wrap structurally-equal values, and merging them would
     * splice unrelated call sites into one instance.
     */
    fun childInstances(): List<ChildInstance<G, N, E>> {
        // Insertion-ordered so the instance order follows node order rather than identity hash
        // codes; lookups stay identity-based because N is a netlist node with no equals override.
        val portsByInstance = LinkedHashMap<N, MutableList<ChildPortNode<G, N, E>>>()
        val order = mutableListOf<ChildPortNode<G, N, E>>()

        childPortNodes().forEach { portNode ->
            val existing = portsByInstance[portNode.value]
            if (existing == null) {
                portsByInstance[portNode.value] = mutableListOf(portNode)
                order.add(portNode)
            } else {
                existing.add(portNode)
            }
        }

        return order.map { first ->
            ChildInstance(
                value = first.value,
                childGraph = first.childGraph,
                ports = portsByInstance.getValue(first.value).toList(),
            )
        }
    }

    data class Flattened<N, E>(
        val graph: WeightedGraph<N, E>,
        /** This graph's own port nodes, mapped to their counterparts in [graph]. */
        val portNodes: Map<Node<N>, WeightedGraph.Node<N>>,
    )

    /**
     * Recursively inlines every child instance, producing a single flat [LeisersonCircuitGraph].
     *
     * [LeisersonCircuitGraph]'s constructor is itself a zero-weight-cycle check, so this throws
     * immediately if the flattened result contains one. Unlike the whole-instance contraction in
     * [HierarchicalLeisersonCircuitGraph], a cycle found here is a real combinational loop: edges
     * are redirected to the specific port they belong to, so external feedback between two ports of
     * one instance only closes a cycle when the child genuinely couples those ports combinationally.
     */
    fun flatten(): LeisersonCircuitGraph<G, N, E> =
        flattenToWeightedGraph().graph.let { LeisersonCircuitGraph(value, it.nodes, it.edges) }

    /** Same inlining as [flatten], but without the cycle validation. */
    fun flattenToWeightedGraph(): Flattened<N, E> {
        val directFlatNode = IdentityHashMap<Node<N>, WeightedGraph.Node<N>>()
        val allFlatNodes = mutableListOf<WeightedGraph.Node<N>>()
        val allFlatEdges = mutableListOf<WeightedGraph.Edge<N, E>>()

        nodes.forEach { node ->
            if (node is LeafNode<N>) {
                val flatNode = WeightedGraph.Node(node.weight, node.value)
                directFlatNode[node] = flatNode
                allFlatNodes.add(flatNode)
            }
        }

        // Each instance is flattened exactly once - flattening per *port* would splice a separate
        // copy of the child's internals in behind every one of its ports.
        childInstances().forEach { instance ->
            val flatChild = instance.childGraph.flattenToWeightedGraph()
            allFlatNodes.addAll(flatChild.graph.nodes)
            allFlatEdges.addAll(flatChild.graph.edges)

            instance.ports.forEach { portNode ->
                directFlatNode[portNode] = flatChild.portNodes[portNode.port]
                    ?: throw IllegalStateException(
                        "Child graph ${instance.childGraph.value} has no flattened node for port ${portNode.port.value}"
                    )
            }
        }

        fun resolve(node: Node<N>) = directFlatNode[node]
            ?: throw IllegalStateException("No flattened node for ${node.value}")

        edges.forEach { edge ->
            allFlatEdges.add(
                WeightedGraph.Edge(
                    weight = edge.weight,
                    source = resolve(edge.source),
                    sink = resolve(edge.sink),
                    value = edge.value,
                )
            )
        }

        val portNodes = IdentityHashMap<Node<N>, WeightedGraph.Node<N>>().apply {
            (inputPorts + outputPorts).forEach { put(it, resolve(it)) }
        }

        return Flattened(
            graph = WeightedGraph(allFlatNodes, allFlatEdges),
            portNodes = portNodes,
        )
    }
}
