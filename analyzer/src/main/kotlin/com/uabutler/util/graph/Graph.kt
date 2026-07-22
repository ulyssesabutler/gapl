package com.uabutler.util.graph

import com.uabutler.util.Logger
import java.util.Collections
import java.util.IdentityHashMap

// Node/edge wrapper types (WeightedGraph.Node, etc.) are structural data classes, so two distinct
// positions in a graph can end up wrapping equal (e.g. weight, value) pairs - notably when the same
// underlying value gets flattened in from multiple call sites sharing one child graph (see
// HierarchicalLeisersonCircuitGraph.flatten - the same function invoked twice with identical
// arguments shares one built module, so each call site's flattening produces separately-constructed
// but structurally-equal node wrappers for its internals). Every lookup in this file is
// identity-based specifically so such coincidentally-equal-but-distinct instances are never merged;
// relying on structural equals/hashCode here previously let repeated same-invocation call sites
// collapse into one shared adjacency-list entry, corrupting the graph and producing spurious edges/cycles.
private fun <K, V> identityMapOf(): MutableMap<K, V> = IdentityHashMap()
private fun <K> identitySetOf(): MutableSet<K> = Collections.newSetFromMap(IdentityHashMap())

interface GraphNode<N> {
    val value: N
}

interface GraphEdge<N, E, NodeT : GraphNode<N>> {
    val source: NodeT
    val sink: NodeT
    val value: E
}

open class Graph<N, E, NodeT : GraphNode<N>, EdgeT : GraphEdge<N, E, NodeT>,  G: Graph<N, E, NodeT, EdgeT, G>>(
    val nodes: Collection<NodeT>,
    val edges: Collection<EdgeT>,
) {
    protected val adjacencyList: Map<NodeT, List<EdgeT>> = identityMapOf<NodeT, MutableList<EdgeT>>().apply {
        edges.forEach { edge -> getOrPut(edge.source) { mutableListOf() }.add(edge) }
    }
    protected fun outgoingEdges(node: NodeT) = adjacencyList[node] ?: emptyList()

    protected open fun newGraph(
        nodes: Collection<NodeT>,
        edges: Collection<EdgeT>,
    ): G {
        throw NotImplementedError("Subclass must implement newGraph()")
    }

    fun topologicalSort(): List<NodeT> {
        // Kahn's algorithm
        val nodesWithIncoming = identityMapOf<NodeT, MutableList<NodeT>>().apply {
            edges.forEach { edge -> getOrPut(edge.sink) { mutableListOf() }.add(edge.source) }
        }
        val inNodes = identityMapOf<NodeT, MutableList<NodeT>>().apply {
            nodes.forEach { put(it, nodesWithIncoming[it] ?: mutableListOf()) }
        }

        val currentStartNodes = inNodes.filterValues { it.isEmpty() }.keys.toMutableList()

        return buildList {
            while (currentStartNodes.isNotEmpty()) {
                val currentNode = currentStartNodes.removeLast()

                add(currentNode)

                adjacencyList[currentNode]?.forEach { edge ->
                    val currentList = inNodes[edge.sink]!!
                    val removeIndex = currentList.indexOfFirst { it === edge.source }
                    if (removeIndex >= 0) currentList.removeAt(removeIndex)
                    if (currentList.isEmpty()) currentStartNodes.add(edge.sink)
                }
            }
        }.also { candidateList ->
            if (candidateList.size != nodes.size) throw IllegalArgumentException("Graph contains cycles").also {
                Logger.debug { "Error in topological sort: Graph contains cycles" }
                Logger.run("SCC Edges") {
                    val visited = identitySetOf<NodeT>().apply { addAll(candidateList) }
                    val sccNodes = nodes.filterNot { it in visited }
                    sccNodes.flatMap { sink -> inNodes[sink]!!.map { source -> source.value to sink.value } }
                        .forEach { (source, sink) -> Logger.debug { "$source -> $sink" } }
                }
            }
        }
    }

    fun rootNodes(): List<NodeT> {
        val sinks = identitySetOf<NodeT>().apply { edges.forEach { add(it.sink) } }
        return nodes.filterNot { it in sinks }
    }

    fun leafNodes(): List<NodeT> {
        val sources = identitySetOf<NodeT>().apply { edges.forEach { add(it.source) } }
        return nodes.filterNot { it in sources }
    }

    fun subgraph(
        nodeFilter: (NodeT) -> Boolean = { true },
        edgeFilter: (EdgeT) -> Boolean = { true },
    ): G {
        val keptNodes = identitySetOf<NodeT>().apply { nodes.filter(nodeFilter).forEach { add(it) } }
        val keptEdges = edges
            .asSequence()
            .filter(edgeFilter)
            .filter { it.source in keptNodes && it.sink in keptNodes }
            .toList()

        return newGraph(
            nodes = keptNodes.toList(),
            edges = keptEdges,
        )
    }

    data class SccSubgraph<NodeT, EdgeT>(
        val members: Set<NodeT>,
        val internalEdges: Set<EdgeT>,
    )

    data class SccNode<NodeT, EdgeT>(
        val id: Int,
        override val value: SccSubgraph<NodeT, EdgeT>,
    ) : GraphNode<SccSubgraph<NodeT, EdgeT>>

    data class SccEdge<NodeT, EdgeT>(
        override val source: SccNode<NodeT, EdgeT>,
        override val sink: SccNode<NodeT, EdgeT>,
        override val value: List<EdgeT>, // all original edges crossing SCCs
    ) : GraphEdge<SccSubgraph<NodeT, EdgeT>, List<EdgeT>, SccNode<NodeT, EdgeT>>

    // Iterative (not recursive) Tarjan's algorithm - a real GAPL design's netlist can easily be
    // deep enough (thousands of chained nodes) to blow the JVM's call stack with a naive recursive
    // DFS, once something (e.g. a whole-program combinational-loop check) exercises this on every
    // compile rather than only on designs that opt into retiming. Each stack frame explicitly
    // tracks which outgoing edge of its node it's currently examining, mirroring exactly what the
    // recursive version's call stack would otherwise track implicitly.
    private class DfsFrame<NodeT>(val node: NodeT, var edgeIndex: Int = 0)

    fun stronglyConnectedComponentsTarjan(): List<Set<NodeT>> {
        var currentIndex = 0
        val indexByNode = identityMapOf<NodeT, Int>()
        val lowLinkByNode = identityMapOf<NodeT, Int>()
        val onStack = identitySetOf<NodeT>()
        val stack = ArrayDeque<NodeT>()
        val components = mutableListOf<Set<NodeT>>()

        fun visit(start: NodeT) {
            val callStack = ArrayDeque<DfsFrame<NodeT>>()

            fun open(node: NodeT) {
                indexByNode[node] = currentIndex
                lowLinkByNode[node] = currentIndex
                currentIndex += 1
                stack.addLast(node)
                onStack.add(node)
                callStack.addLast(DfsFrame(node))
            }

            open(start)

            while (callStack.isNotEmpty()) {
                val frame = callStack.last()
                val node = frame.node
                val outgoing = outgoingEdges(node)

                if (frame.edgeIndex < outgoing.size) {
                    val edge = outgoing[frame.edgeIndex]
                    frame.edgeIndex += 1
                    val nextNode = edge.sink

                    if (nextNode !in indexByNode) {
                        open(nextNode)
                    } else if (nextNode in onStack) {
                        lowLinkByNode[node] = minOf(lowLinkByNode.getValue(node), indexByNode.getValue(nextNode))
                    }
                } else {
                    callStack.removeLast()
                    val parentFrame = callStack.lastOrNull()
                    if (parentFrame != null) {
                        val parent = parentFrame.node
                        lowLinkByNode[parent] = minOf(lowLinkByNode.getValue(parent), lowLinkByNode.getValue(node))
                    }

                    if (lowLinkByNode.getValue(node) == indexByNode.getValue(node)) {
                        val component = identitySetOf<NodeT>()
                        while (true) {
                            val w = stack.removeLast()
                            onStack.remove(w)
                            component.add(w)
                            if (w === node) break
                        }
                        components.add(component)
                    }
                }
            }
        }

        for (node in nodes) {
            if (node !in indexByNode) visit(node)
        }

        return components
    }

    class CondensedDag<NodeT, EdgeT>(
        nodes: Collection<SccNode<NodeT, EdgeT>>,
        edges: Collection<SccEdge<NodeT, EdgeT>>,
    ) : Graph<
            SccSubgraph<NodeT, EdgeT>,
            List<EdgeT>,
            SccNode<NodeT, EdgeT>,
            SccEdge<NodeT, EdgeT>,
            CondensedDag<NodeT, EdgeT>
            >(nodes, edges) {

        override fun newGraph(
            nodes: Collection<SccNode<NodeT, EdgeT>>,
            edges: Collection<SccEdge<NodeT, EdgeT>>,
        ): CondensedDag<NodeT, EdgeT> = CondensedDag(nodes, edges)
    }

    fun condenseToDag(): CondensedDag<NodeT, EdgeT> {
        val sccs: List<Set<NodeT>> = stronglyConnectedComponentsTarjan()

        // Map each original node -> SCC id
        val nodeToSccId = identityMapOf<NodeT, Int>().apply {
            sccs.forEachIndexed { index, component -> component.forEach { put(it, index) } }
        }

        // Internal edges per SCC
        val internalEdgesByScc: Array<MutableSet<EdgeT>> = Array(sccs.size) { mutableSetOf() }
        for (edge in edges) {
            val srcId = nodeToSccId.getValue(edge.source)
            val dstId = nodeToSccId.getValue(edge.sink)
            if (srcId == dstId) internalEdgesByScc[srcId].add(edge)
        }

        // Build SCC nodes
        val dagNodes: List<SccNode<NodeT, EdgeT>> = sccs.mapIndexed { id, members ->
            SccNode(
                id = id,
                value = SccSubgraph(
                    members = members.toSet(),
                    internalEdges = internalEdgesByScc[id].toSet()
                )
            )
        }
        val idToDagNode = dagNodes.associateBy { it.id }

        // Aggregate crossing edges by (srcScc, dstScc)
        val crossingEdgesGrouped = mutableMapOf<Pair<Int, Int>, MutableList<EdgeT>>()
        for (edge in edges) {
            val srcId = nodeToSccId.getValue(edge.source)
            val dstId = nodeToSccId.getValue(edge.sink)
            if (srcId != dstId) {
                crossingEdgesGrouped.getOrPut(srcId to dstId) { mutableListOf() }.add(edge)
            }
        }

        val dagEdges: List<SccEdge<NodeT, EdgeT>> = crossingEdgesGrouped.entries.map { (key, crossingEdges) ->
            val (srcId, dstId) = key
            SccEdge(
                source = idToDagNode.getValue(srcId),
                sink = idToDagNode.getValue(dstId),
                value = crossingEdges.toList()
            )
        }

        return CondensedDag(dagNodes, dagEdges)
    }
}

class UnweightedGraph<N, E>(
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
) : Graph<N, E, UnweightedGraph.Node<N>, UnweightedGraph.Edge<N, E>, UnweightedGraph<N, E>>(nodes, edges) {

    data class Node<N>(override val value: N) : GraphNode<N>

    data class Edge<N, E>(
        override val source: Node<N>,
        override val sink: Node<N>,
        override val value: E,
    ) : GraphEdge<N, E, Node<N>>
}

