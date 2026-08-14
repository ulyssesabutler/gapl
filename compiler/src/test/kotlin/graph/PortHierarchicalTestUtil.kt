package graph

import com.uabutler.netlistir.transformer.util.retiming.solver.PerPortHierarchicalMinimalRegisterSolver
import com.uabutler.util.graph.PortHierarchicalCircuitGraph

typealias PortGraph = PortHierarchicalCircuitGraph<String, String, String>
typealias PortLeaf = PortHierarchicalCircuitGraph.LeafNode<String>
typealias PortEdge = PortHierarchicalCircuitGraph.Edge<String, String>

/** A call site: which child graph, and what to call the instance. */
data class ChildInstanceSketch(
    val instance: String,
    val childGraph: PortGraph,
)

object PortHierarchicalTestUtil {

    /**
     * Builds a per-port hierarchical graph from an edge list.
     *
     * Node names are plain strings. A name of the form `"<instance>.<port>"` refers to a port of a
     * child instance declared in [childInstances]; everything else becomes a [PortLeaf]. [inputPorts]
     * and [outputPorts] name this graph's own boundary - unlike the whole-module solver's harness,
     * there are no super-nodes, so these are ordinary nodes that happen to be the boundary.
     */
    fun createPortGraph(
        name: String,
        edgeList: List<Edge>,
        inputPorts: List<String>,
        outputPorts: List<String>,
        leafWeights: Map<String, Int> = emptyMap(),
        childInstances: List<ChildInstanceSketch> = emptyList(),
    ): PortGraph {
        val childByInstance = childInstances.associateBy { it.instance }

        fun isChildPort(nodeName: String) =
            nodeName.substringBefore('.', "") in childByInstance.keys

        val leafNames = (edgeList.flatMap { listOf(it.source, it.sink) } + inputPorts + outputPorts)
            .filterNot { isChildPort(it) }
            .toSet()

        val leaves = leafNames.associateWith { PortLeaf(it, leafWeights[it] ?: 1) }

        // Port nodes carry no delay of their own, matching how the netlist converter weights IONodes.
        val boundary = (inputPorts + outputPorts).associateWith { PortLeaf(it, 0) }
        fun leafFor(nodeName: String) = boundary[nodeName] ?: leaves.getValue(nodeName)

        val childPortNodes = mutableMapOf<String, PortHierarchicalCircuitGraph.ChildPortNode<String, String, String>>()
        edgeList.flatMap { listOf(it.source, it.sink) }.filter { isChildPort(it) }.distinct().forEach { nodeName ->
            val instanceName = nodeName.substringBefore('.')
            val portName = nodeName.substringAfter('.')
            val sketch = childByInstance.getValue(instanceName)
            val isInput = sketch.childGraph.inputPorts.any { it.value == portName }
            val port = (sketch.childGraph.inputPorts + sketch.childGraph.outputPorts)
                .firstOrNull { it.value == portName }
                ?: error("Child ${sketch.childGraph.value} has no port '$portName'")

            childPortNodes[nodeName] = PortHierarchicalCircuitGraph.ChildPortNode(
                value = instanceName,
                childGraph = sketch.childGraph,
                port = port,
                isInput = isInput,
            )
        }

        fun nodeFor(nodeName: String): PortHierarchicalCircuitGraph.Node<String> =
            childPortNodes[nodeName] ?: leafFor(nodeName)

        val edges = edgeList.map { sketch ->
            PortEdge(
                source = nodeFor(sketch.source),
                sink = nodeFor(sketch.sink),
                value = sketch.value(),
                weight = sketch.weight,
            )
        }

        val allNodes = (leafNames.map { leafFor(it) } + boundary.values + childPortNodes.values).distinct()

        return PortGraph(
            value = name,
            nodes = allNodes,
            edges = edges,
            inputPorts = inputPorts.map { boundary.getValue(it) },
            outputPorts = outputPorts.map { boundary.getValue(it) },
        )
    }

    fun edgeWeight(graph: PortGraph, sketch: Edge): Int =
        graph.edges.first { it.value == sketch.value() }.weight

    fun solve(graphs: List<PortGraph>, targetClockPeriod: Int): Map<PortGraph, PortGraph>? {
        var counter = 0
        val solver = PerPortHierarchicalMinimalRegisterSolver(
            graphs = graphs,
            expansionNodeFactory = { "port-expansion-${counter++}" },
            expansionEdgeValueFactory = { "port-expansion-edge" },
        )
        val retimed = solver.solveOrNull(targetClockPeriod) ?: return null
        return graphs.zip(retimed.graphs).toMap()
    }
}
