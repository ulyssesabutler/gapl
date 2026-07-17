package graph

import com.uabutler.util.graph.NewHierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.util.NewHierarchicalMinimalRegisterSolver

typealias HGraph = NewHierarchicalLeisersonCircuitGraph<String, String, String>
typealias HEdge = NewHierarchicalLeisersonCircuitGraph.Edge<String, String>
typealias HSolveResult = NewHierarchicalMinimalRegisterSolver.SolveResult<String, String, String>

data class HierarchicalEdgeSketch(
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
     *  - nodes in [virtualNodes] become VirtualNode (weight 0, I/O boundaries)
     *  - nodes in [childGraphs] become ChildGraphNode referencing the given sub-graph
     *  - all other nodes in [edgeList] become LeafNode (weight from [leafWeights], defaulting to 1)
     */
    fun createHierarchicalGraph(
        name: String,
        edgeList: List<HierarchicalEdgeSketch>,
        leafWeights: Map<String, Int> = emptyMap(),
        childGraphs: Map<String, HGraph> = emptyMap(),
        virtualNodes: Set<String> = emptySet(),
    ): HGraph {
        val allNodeNames = (
            edgeList.flatMap { listOf(it.source, it.sink) } +
            childGraphs.keys +
            virtualNodes
        ).toSet()

        val nodes: Map<String, NewHierarchicalLeisersonCircuitGraph.Node<String>> = allNodeNames.associateWith { nodeName ->
            when {
                nodeName in virtualNodes ->
                    NewHierarchicalLeisersonCircuitGraph.VirtualNode(nodeName)
                nodeName in childGraphs ->
                    NewHierarchicalLeisersonCircuitGraph.ChildGraphNode(
                        value = nodeName,
                        childGraph = childGraphs[nodeName]!!,
                    )
                else ->
                    NewHierarchicalLeisersonCircuitGraph.LeafNode(
                        value = nodeName,
                        weight = leafWeights[nodeName] ?: 1,
                    )
            }
        }

        val edges = edgeList.map { sketch ->
            NewHierarchicalLeisersonCircuitGraph.Edge(
                source = nodes[sketch.source]!!,
                sink = nodes[sketch.sink]!!,
                value = sketch.value(),
                weight = sketch.weight,
            )
        }

        return HGraph(value = name, nodes = nodes.values, edges = edges)
    }

    fun getCorrespondingEdge(edges: Collection<HEdge>, sketch: HierarchicalEdgeSketch): HEdge =
        edges.first { it.value == sketch.value() }

    fun solve(
        graphs: Collection<HGraph>,
        targetClockPeriod: Int,
    ): Map<HGraph, HSolveResult> {
        var counter = 0
        return NewHierarchicalMinimalRegisterSolver(
            graphs = graphs,
            expansionNodeFactory = { "expansion-${counter++}" },
            expansionEdgeValueFactory = { "expansion-edge" },
        ).solveAll(targetClockPeriod)
    }

    fun solve(graph: HGraph, targetClockPeriod: Int): HSolveResult? =
        solve(listOf(graph), targetClockPeriod)[graph]
}
