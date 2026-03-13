package com.uabutler.util.graph

import com.uabutler.util.Logger

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
    protected val adjacencyList: Map<NodeT, List<EdgeT>> = edges.groupBy { it.source }
    protected fun outgoingEdges(node: NodeT) = adjacencyList[node] ?: emptyList()

    protected open fun newGraph(
        nodes: Collection<NodeT>,
        edges: Collection<EdgeT>,
    ): G {
        throw NotImplementedError("Subclass must implement newGraph()")
    }

    fun topologicalSort(): List<NodeT> {
        // Kahn's algorithm
        val nodesWithIncoming = edges.groupBy { it.sink }.mapValues { (_, edges) -> edges.map { it.source }.toMutableList() }
        val inNodes = nodes.associateWith { nodesWithIncoming[it] ?: mutableListOf() }

        val currentStartNodes = inNodes.filterValues { it.isEmpty() }.keys.toMutableList()

        return buildList {
            while (currentStartNodes.isNotEmpty()) {
                val currentNode = currentStartNodes.removeLast()

                add(currentNode)

                adjacencyList[currentNode]?.forEach { edge ->
                    val currentList = inNodes[edge.sink]!!
                    currentList.remove(edge.source)
                    if (currentList.isEmpty()) currentStartNodes.add(edge.sink)
                }
            }
        }.also { candidateList ->
            if (candidateList.size != nodes.size) throw IllegalArgumentException("Graph contains cycles").also {
                Logger.debug { "Error in topological sort: Graph contains cycles" }
                Logger.run("SCC Edges") {
                    val sccNodes = nodes - candidateList.toSet()
                    sccNodes.flatMap { sink -> inNodes[sink]!!.map { source -> source.value to sink.value } }
                        .forEach { (source, sink) -> Logger.debug { "$source -> $sink" } }
                }
            }
        }
    }

    fun rootNodes(): List<NodeT> = nodes - edges.map { it.sink }.toSet()

    fun leafNodes(): List<NodeT> = nodes - edges.map { it.source }.toSet()

    fun subgraph(
        nodeFilter: (NodeT) -> Boolean = { true },
        edgeFilter: (EdgeT) -> Boolean = { true },
    ): G {
        val keptNodes = nodes.filter(nodeFilter).toSet()
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

    fun stronglyConnectedComponentsTarjan(): List<Set<NodeT>> {
        var currentIndex = 0
        val indexByNode = mutableMapOf<NodeT, Int>()
        val lowLinkByNode = mutableMapOf<NodeT, Int>()
        val onStack = mutableSetOf<NodeT>()
        val stack = ArrayDeque<NodeT>()
        val components = mutableListOf<Set<NodeT>>()

        fun dfs(node: NodeT) {
            indexByNode[node] = currentIndex
            lowLinkByNode[node] = currentIndex
            currentIndex += 1

            stack.addLast(node)
            onStack.add(node)

            for (edge in outgoingEdges(node)) {
                val nextNode = edge.sink
                if (nextNode !in indexByNode) {
                    dfs(nextNode)
                    lowLinkByNode[node] = minOf(lowLinkByNode.getValue(node), lowLinkByNode.getValue(nextNode))
                } else if (nextNode in onStack) {
                    lowLinkByNode[node] = minOf(lowLinkByNode.getValue(node), indexByNode.getValue(nextNode))
                }
            }

            if (lowLinkByNode.getValue(node) == indexByNode.getValue(node)) {
                val component = mutableSetOf<NodeT>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                    if (w == node) break
                }
                components.add(component)
            }
        }

        for (node in nodes) {
            if (node !in indexByNode) dfs(node)
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
        val nodeToSccId: Map<NodeT, Int> =
            sccs.flatMapIndexed { index, component -> component.map { it to index } }.toMap()

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

