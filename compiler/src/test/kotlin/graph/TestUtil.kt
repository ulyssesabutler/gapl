package graph

import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

typealias Graph = LeisersonCircuitGraph<String, String, String>
typealias HierarchicalGraph = HierarchicalLeisersonCircuitGraph<String, String, String>
typealias ContractedGraph = HierarchicalLeisersonCircuitGraph.ContractCircuitGraph<String, String>
typealias Node = WeightedGraph.Node<String>
typealias Edge = WeightedGraph.Edge<String, String>

data class EdgeSketch(
    val source: String,
    val sink: String,
    val weight: Int,
) {
    fun value() = "$source -> $sink"
    override fun toString() = value()
}

data class ContractSketch(
    val inputOutputDelay: Pair<Int, Int>? = null,
    val combinationalDelay: Int? = null,
    val addedRegisters: Int
)


fun WeightedGraph<String, String>.print() {
    println("Graph")

    println("  Nodes:")
    nodes.forEach { node ->
        println("    ${node.value}: ${node.weight}")
    }

    println("  Edges:")
    edges.forEach { edge ->
        // println("    ${edge.value}: ${edge.weight}")
        println("    ${edge.source.value} -> ${edge.sink.value}: ${edge.weight}")
    }
}

object TestUtil {

    fun createGraph(name: String, edgeList: List<EdgeSketch>, weightOverride: Map<String, Int> = emptyMap()): Graph {
        val nodes = edgeList
            .flatMap { listOf(it.source, it.sink) }
            .toSet()
            .associateWith { weightOverride[it] ?: 1 }
            .map { (node, weight) -> WeightedGraph.Node(weight, node) }
            .associateBy { it.value }

        val edges = edgeList.map {
            WeightedGraph.Edge(
                source = nodes[it.source]!!,
                sink = nodes[it.sink]!!,
                value = it.value(),
                weight = it.weight,
            )
        }

        return Graph(
            value = name,
            nodes = nodes.values.toList(),
            edges = edges,
        )
    }

    fun ContractSketch.toActual(name: String): ContractActual {
        val inputNode = WeightedGraph.Node(0, "$name-input")
        val outputNode = WeightedGraph.Node(0, "$name-output")

        var inputDelayNode: Node? = null
        var outputDelayNode: Node? = null
        var combinationalDelayNode: Node? = null

        var inputRegisterDelayEdge: Edge? = null
        var outputRegisterDelayEdge: Edge? = null
        var registerDelayEdge: Edge? = null

        var inputCombinationalDelayEdge: Edge? = null
        var outputCombinationalDelayEdge: Edge? = null

        if (inputOutputDelay != null) {
            inputDelayNode = Node(inputOutputDelay.first, "$name-input-delay")
            outputDelayNode = Node(inputOutputDelay.second, "$name-output-delay")

            inputRegisterDelayEdge = Edge(
                source = inputNode,
                sink = inputDelayNode,
                weight = 0,
                value = "${inputNode.value} -> ${inputDelayNode.value}",
            )
            outputRegisterDelayEdge = Edge(
                source = outputDelayNode,
                sink = outputNode,
                weight = 0,
                value = "${outputDelayNode.value} -> ${outputNode.value}",
            )
            registerDelayEdge = Edge(
                source = inputDelayNode,
                sink = outputDelayNode,
                weight = 0,
                value = "${inputDelayNode.value} -> ${outputDelayNode.value}",
            )
        }

        if (combinationalDelay != null) {
            combinationalDelayNode = WeightedGraph.Node(combinationalDelay, "$name-combinational-delay")

            inputCombinationalDelayEdge = WeightedGraph.Edge(
                source = inputNode,
                sink = combinationalDelayNode,
                weight = 0,
                value = "${inputNode.value} -> ${combinationalDelayNode.value}",
            )
            outputCombinationalDelayEdge = WeightedGraph.Edge(
                source = combinationalDelayNode,
                sink = outputNode,
                weight = 0,
                value = "${combinationalDelayNode.value} -> ${outputNode.value}",
            )
        }

        return ContractActual(
            inputNode = inputNode,
            outputNode = outputNode,
            inputDelayNode = inputDelayNode,
            outputDelayNode = outputDelayNode,
            combinationalDelayNode = combinationalDelayNode,
            inputRegisterDelayEdge = inputRegisterDelayEdge,
            outputRegisterDelayEdge = outputRegisterDelayEdge,
            registerDelayEdge = registerDelayEdge,
            inputCombinationalDelayEdge = inputCombinationalDelayEdge,
            outputCombinationalDelayEdge = outputCombinationalDelayEdge,
            addedRegisters = addedRegisters,
        )
    }

    data class ContractActual(
        val inputNode: Node,
        val outputNode: Node,

        val inputDelayNode: Node?,
        val outputDelayNode: Node?,
        val combinationalDelayNode: Node?,

        val inputRegisterDelayEdge: Edge?,
        val outputRegisterDelayEdge: Edge?,
        val registerDelayEdge: Edge?,

        val inputCombinationalDelayEdge: Edge?,
        val outputCombinationalDelayEdge: Edge?,

        val addedRegisters: Int,
    ) {
        fun nodes() = listOfNotNull(inputNode, outputNode, inputDelayNode, outputDelayNode, combinationalDelayNode)
        fun edges() = listOfNotNull(inputRegisterDelayEdge, outputRegisterDelayEdge, registerDelayEdge, inputCombinationalDelayEdge, outputCombinationalDelayEdge)

        fun toContractedGraph(
            incomingEdges: List<Edge>,
            outgoingEdges: List<Edge>,
        ): ContractedGraph {
            return ContractedGraph(
                retimedInputDelay = inputDelayNode?.weight,
                retimedOutputDelay = outputDelayNode?.weight,
                retimedCombinationalDelay = combinationalDelayNode?.weight,
                unretimedRegisterDelay = 0,
                retimedRegisterDelay = addedRegisters,
                originalIncomingEdges = emptyList(),
                originalOutgoingEdges = emptyList(),
                contractedIncomingEdges = incomingEdges,
                contractedOutgoingEdges = outgoingEdges,
                originalNode = Node(0, "original"),
                contractedInputNode = inputNode,
                contractedOutputNode = outputNode,
                contractedInputDelayNode = inputDelayNode,
                contractedOutputDelayNode = outputDelayNode,
                contractedInputDelayEdge = inputRegisterDelayEdge,
                contractedOutputDelayEdge = outputRegisterDelayEdge,
                contractedRegisterDelayEdge = registerDelayEdge,
                contractedCombinationalDelayNode = combinationalDelayNode,
                contractedCombinationalDelayInputEdge = inputCombinationalDelayEdge,
                contractedCombinationalDelayOutputEdge = outputCombinationalDelayEdge,
            )
        }
    }

    fun createHierarchicalGraph(
        name: String,
        edgeList: List<EdgeSketch>,
        weightOverride: Map<String, Int> = emptyMap(),
        contractedOverride: Map<String, ContractSketch>,
    ): HierarchicalGraph {
        val contractedGraphNames = contractedOverride.keys

        println(contractedGraphNames)

        val actualContractedGraphs = contractedOverride.map { (label, contractSketch) -> label to contractSketch.toActual(label) }.toMap()
        val incomingEdges: MutableMap<String, List<Edge>> = contractedOverride.mapValues { emptyList<Edge>() }.toMutableMap()
        val outgoingEdges: MutableMap<String, List<Edge>> = contractedOverride.mapValues { emptyList<Edge>() }.toMutableMap()

        val parentNodes = edgeList
            .flatMap { listOf(it.source, it.sink) }
            .toSet()
            .filter { it !in contractedGraphNames }
            .associateWith { weightOverride[it] ?: 1 }
            .map { (node, weight) -> WeightedGraph.Node(weight, node) }
            .associateBy { it.value }

        val nodes = parentNodes.values.toList() + actualContractedGraphs.flatMap { it.value.nodes() }

        val parentEdges = edgeList.map {
            println("Doing ${it.value()}")

            val sourceNode = if (it.source !in contractedGraphNames) {
                println("Source is not contracted, using parent node")
                parentNodes[it.source]!!
            } else {
                println("Source is contracted, using actual contracted node")
                actualContractedGraphs[it.source]!!.outputNode
            }

            val sinkNode = if (it.sink !in contractedGraphNames) {
                println("Sink is not contracted, using parent node")
                parentNodes[it.sink]!!
            } else {
                println("Sink is contracted, using actual contracted node")
                actualContractedGraphs[it.sink]!!.inputNode
            }

            WeightedGraph.Edge(
                source = sourceNode,
                sink = sinkNode,
                value = it.value(),
                weight = it.weight,
            ).also { edge ->
                if (it.source in contractedGraphNames) {
                    outgoingEdges[it.source] = outgoingEdges.getValue(it.source) + edge
                }

                if (it.sink in contractedGraphNames) {
                    incomingEdges[it.sink] = incomingEdges.getValue(it.sink) + edge
                }
            }
        }

        val edges = parentEdges + actualContractedGraphs.flatMap { it.value.edges() }

        val contractedGraphs = actualContractedGraphs.map { (label, contractActual) ->
            contractActual.toContractedGraph(
                incomingEdges = incomingEdges[label]!!,
                outgoingEdges = outgoingEdges[label]!!,
            )
        }

        return HierarchicalGraph(
            value = name,
            nodes = nodes,
            edges = edges,
            contractCircuitGraphs = contractedGraphs,
        )
    }

    fun getCorrespondingEdge(edgeList: Collection<WeightedGraph.Edge<String, String>>, edge: EdgeSketch): WeightedGraph.Edge<String, String> {
        return edgeList.first { it.value == edge.value() }
    }

    fun getSublistSums(list: List<Int>): Set<Int> {
        if (list.isEmpty()) return emptySet()

        fun prefixSums(list: List<Int>) = list.runningFold(0) { acc, i -> acc + i }

        val first = list.first()
        val prefixSums = prefixSums(list.drop(1)).toSet()

        return buildSet {
            add(first)
            addAll(prefixSums.map { it + first })
            addAll(getSublistSums(list.drop(1)))
        }
    }

}