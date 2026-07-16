package com.uabutler.util.graph

import com.uabutler.util.Logger
import java.util.PriorityQueue

open class LeisersonCircuitGraph<G, N, E>(
    val value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
): WeightedGraph<N, E>(nodes, edges) {

    init {
        if (nodes.any { it.weight < 0 }) throw IllegalArgumentException("Node weights must be non-negative")
        // if (edges.any { it.weight < 0 }) throw IllegalArgumentException("Edge weights must be non-negative")

        try {
            subgraph(edgeFilter = { it.weight == 0 }).topologicalSort()
        } catch (_: Exception) {
            throw IllegalArgumentException("Graph cannot contain zero-weight cycles")
        }
    }

    companion object {

        private fun <N, E> dijkstraInternal(
            startNode: Node<N>,
            sccMembers: Set<Node<N>>,
            internalAdj: Map<Node<N>, List<Edge<N, E>>>,
            edgeRegisters: (Edge<N, E>) -> Int,
        ): Map<Node<N>, Int> {
            val distance = mutableMapOf<Node<N>, Int>()
            val visited = mutableSetOf<Node<N>>()

            val pq = PriorityQueue(compareBy<Pair<Int, Node<N>>> { it.first })
            distance[startNode] = 0
            pq.add(0 to startNode)

            while (pq.isNotEmpty()) {
                val (distU, u) = pq.poll()
                if (u in visited) continue
                visited.add(u)

                for (edge in internalAdj[u].orEmpty()) {
                    val v = edge.sink
                    if (v !in sccMembers) continue

                    val candidate = distU + edgeRegisters(edge)
                    val current = distance[v]
                    if (current == null || candidate < current) {
                        distance[v] = candidate
                        pq.add(candidate to v)
                    }
                }
            }

            // Nodes not reached simply won't be present
            return distance
        }

    }

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

    data class FastestConnection<N>(
        val source: Node<N>,
        val sink: Node<N>,
        val delay: Int,
        val registerCount: Int,
    )

    fun findFastestConnectionsFromNode(sourceNode: Node<N>): List<FastestConnection<N>> {
        return shortestPathsFromNode(
            root = sourceNode,
            edgeWeight = { it.weight to -it.source.weight },
            weightComparator = compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
            weightAddition = { a, b -> a.first + b.first to a.second + b.second },
            zero = 0 to 0,
        ).map { (sinkNode, distance) ->
            FastestConnection(sourceNode, sinkNode, sinkNode.weight - distance.second, distance.first)
        }
    }

    fun computePossibleClockPeriods() = Logger.run("Computing possible clock periods") {
        nodes.asSequence()
            .map { findFastestConnectionsFromNode(it) }
            .flatMap { it.asSequence() }
            .map { it.delay }
            .toSet()
            .also { Logger.debug { "Found ${it.size} possible clock periods" } }
    }

    fun registerDelaysFrom(sourceNode: Node<N>): Map<Node<N>, List<Int>> {
        require(sourceNode in nodes) { "sourceNode must be a node in this graph." }

        // 1) SCCs
        val sccs: List<Set<Node<N>>> = stronglyConnectedComponentsTarjan()
        val sccIdByNode: Map<Node<N>, Int> =
            sccs.flatMapIndexed { id, component -> component.map { it to id } }.toMap()

        // 2) Condensation DAG over SCC ids (acyclic)
        val condensation = condenseToDag() // edges carry List<originalEdge>
        val sccDagNodes: List<SccNode<Node<N>, Edge<N, E>>> = condensation.nodes.toList()
        val sccDagEdges: List<SccEdge<Node<N>, Edge<N, E>>> = condensation.edges.toList()

        // Topological order of SCC nodes (it is a DAG)
        val topoSccs: List<SccNode<Node<N>, Edge<N, E>>> = condensation.topologicalSort()

        // 3) Build internal adjacency per SCC for internal shortest paths
        val internalAdjByScc: Array<Map<Node<N>, List<Edge<N, E>>>> =
            Array(sccs.size) { emptyMap() }

        run {
            val internalEdgesByScc: Array<MutableList<Edge<N, E>>> = Array(sccs.size) { mutableListOf() }
            for (edge in edges) {
                val srcScc = sccIdByNode.getValue(edge.source)
                val dstScc = sccIdByNode.getValue(edge.sink)
                if (srcScc == dstScc) internalEdgesByScc[srcScc].add(edge)
            }
            for (sccId in sccs.indices) {
                internalAdjByScc[sccId] = internalEdgesByScc[sccId].groupBy { it.source }
            }
        }

        // 4) Determine "entry nodes" into each SCC (sinks of cross-SCC edges), plus the source node’s SCC entry
        val entryNodesByScc: Array<MutableSet<Node<N>>> = Array(sccs.size) { mutableSetOf() }
        for (dagEdge in sccDagEdges) {
            // Each condensed edge aggregates original crossing edges
            for (originalEdge in dagEdge.value) {
                val dstScc = sccIdByNode.getValue(originalEdge.sink)
                entryNodesByScc[dstScc].add(originalEdge.sink)
            }
        }
        entryNodesByScc[sccIdByNode.getValue(sourceNode)].add(sourceNode)

        // 5) Cache internal shortest paths within SCC from relevant entry nodes
        //    distancesWithinScc[sccId][entryNode] = Map<nodeInScc, distRegisters>
        val distancesWithinScc: Array<MutableMap<Node<N>, Map<Node<N>, Int>>> =
            Array(sccs.size) { mutableMapOf() }

        fun internalShortestDistances(sccId: Int, entry: Node<N>): Map<Node<N>, Int> {
            return distancesWithinScc[sccId].getOrPut(entry) {
                dijkstraInternal(
                    startNode = entry,
                    sccMembers = sccs[sccId],
                    internalAdj = internalAdjByScc[sccId],
                    edgeRegisters = { it.weight },
                )
            }
        }

        // 6) DP state:
        //    For each SCC, maintain delay sets to specific "arrival nodes" inside the SCC.
        val arrivalDelaysByNode: MutableMap<Node<N>, MutableSet<Int>> = mutableMapOf()
        arrivalDelaysByNode.getOrPut(sourceNode) { mutableSetOf() }.add(0)

        // Final output: delays to every node
        val delaysToNode: MutableMap<Node<N>, MutableSet<Int>> =
            nodes.associateWith { mutableSetOf<Int>() }.toMutableMap()
        delaysToNode.getValue(sourceNode).add(0)

        // Precompute outgoing cross edges grouped by SCC for propagation convenience
        val outgoingCrossEdgesByScc: Array<List<Edge<N, E>>> = run {
            val buckets: Array<MutableList<Edge<N, E>>> = Array(sccs.size) { mutableListOf() }
            for (edge in edges) {
                val src = sccIdByNode.getValue(edge.source)
                val dst = sccIdByNode.getValue(edge.sink)
                if (src != dst) buckets[src].add(edge)
            }
            Array(sccs.size) { buckets[it].toList() }
        }

        // 7) Process SCCs in topological order.
        //    For each SCC:
        //      - From each entry node that has arrival delays, expand to all nodes in SCC using internal shortest distances.
        //      - Then propagate from SCC nodes to next SCC entries using crossing edges.
        for (sccDagNode in topoSccs) {
            val sccId = sccDagNode.id
            val members = sccs[sccId]

            // Which entry nodes in this SCC might we have arrival delays for?
            val entryCandidates = entryNodesByScc[sccId]
                .asSequence()
                .filter { it in arrivalDelaysByNode }
                .toList()

            if (entryCandidates.isEmpty()) continue

            // Expand inside SCC: fill delaysToNode for every member
            for (entry in entryCandidates) {
                val entryArrivalDelays = arrivalDelaysByNode.getValue(entry)
                val distances = internalShortestDistances(sccId, entry)

                for (target in members) {
                    val dist = distances[target] ?: continue // unreachable inside SCC (possible if SCC data is off)
                    val targetSet = delaysToNode.getValue(target)
                    for (base in entryArrivalDelays) {
                        targetSet.add(base + dist)
                    }
                }
            }

            // Propagate to successor SCCs via crossing edges:
            // Need delays to each edge.source (which is in this SCC). Use delaysToNode for that node.
            for (crossEdge in outgoingCrossEdgesByScc[sccId]) {
                val srcNode = crossEdge.source
                val dstNode = crossEdge.sink
                val edgeRegisters = crossEdge.weight

                val srcDelays = delaysToNode[srcNode] ?: continue
                if (srcDelays.isEmpty()) continue

                val dstArrivalSet = arrivalDelaysByNode.getOrPut(dstNode) { mutableSetOf() }
                for (delay in srcDelays) {
                    dstArrivalSet.add(delay + edgeRegisters)
                }
            }
        }

        // Freeze as immutable sets
        return delaysToNode.mapValues { (_, v) -> v.toList().sorted() }
    }

}
