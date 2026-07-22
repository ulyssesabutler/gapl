package graph

import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.util.HierarchicalMinimalRegisterSolver

typealias HGraph = HierarchicalLeisersonCircuitGraph<String, String, String>
typealias HEdge = HierarchicalLeisersonCircuitGraph.Edge<String, String>
typealias HSolveResult = HierarchicalMinimalRegisterSolver.SolveResult<String, String, String>

data class Edge(
    val source: String,
    val sink: String,
    val weight: Int,
) {
    fun value() = "$source -> $sink"
    override fun toString() = value()
}

object HierarchicalTestUtil {

    /**
     * Creates a hierarchical graph where:
     *  - [rootAttachment] and [leafAttachment] become VirtualNode (weight 0, I/O boundaries) -
     *    matching the real convention (HierarchicalNetlistLeisersonCircuitConverter.fromModule)
     *    of exactly one super-input and one super-output node per graph
     *  - nodes in [childGraphs] become ChildGraphNode referencing the given sub-graph
     *  - all other nodes in [edgeList] become LeafNode (weight from [leafWeights], defaulting to 1)
     */
    fun createHierarchicalGraph(
        name: String,
        edgeList: List<Edge>,
        rootAttachment: String,
        leafAttachment: String,
        leafWeights: Map<String, Int> = emptyMap(),
        childGraphs: Map<String, HGraph> = emptyMap(),
    ): HGraph {
        val virtualNodes = setOf(rootAttachment, leafAttachment)

        val allNodeNames = (
            edgeList.flatMap { listOf(it.source, it.sink) } +
            childGraphs.keys +
            virtualNodes
        ).toSet()

        val nodes: Map<String, HierarchicalLeisersonCircuitGraph.Node<String>> = allNodeNames.associateWith { nodeName ->
            when {
                nodeName in virtualNodes ->
                    HierarchicalLeisersonCircuitGraph.VirtualNode(nodeName)
                nodeName in childGraphs ->
                    HierarchicalLeisersonCircuitGraph.ChildGraphNode(
                        value = nodeName,
                        childGraph = childGraphs[nodeName]!!,
                    )
                else ->
                    HierarchicalLeisersonCircuitGraph.LeafNode(
                        value = nodeName,
                        weight = leafWeights[nodeName] ?: 1,
                    )
            }
        }

        val edges = edgeList.map { sketch ->
            HierarchicalLeisersonCircuitGraph.Edge(
                source = nodes[sketch.source]!!,
                sink = nodes[sketch.sink]!!,
                value = sketch.value(),
                weight = sketch.weight,
            )
        }

        return HGraph(
            value = name,
            nodes = nodes.values,
            edges = edges,
            rootAttachment = nodes.getValue(rootAttachment),
            leafAttachment = nodes.getValue(leafAttachment),
        )
    }

    fun getCorrespondingEdge(edges: Collection<HEdge>, sketch: Edge): HEdge =
        edges.first { it.value == sketch.value() }

    fun solve(
        graphs: Collection<HGraph>,
        targetClockPeriod: Int,
    ): Map<HGraph, HSolveResult> {
        var counter = 0
        return HierarchicalMinimalRegisterSolver(
            graphs = graphs,
            expansionNodeFactory = { "expansion-${counter++}" },
            expansionEdgeValueFactory = { "expansion-edge" },
        ).solveAll(targetClockPeriod)
    }

    fun solve(graph: HGraph, targetClockPeriod: Int): HSolveResult? =
        solve(listOf(graph), targetClockPeriod)[graph]
}
