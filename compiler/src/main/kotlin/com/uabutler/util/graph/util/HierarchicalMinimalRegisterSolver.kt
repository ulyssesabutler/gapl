package com.uabutler.util.graph.util

import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

data class TimingProperties(
    val inputDelay: Int?,
    val outputDelay: Int?,
    val combinationalDelay: Int?,
    val registerDelay: Int,
    val clockPeriod: Int,
    val registerCount: Int,
)

class HierarchicalMinimalRegisterSolver<G, N, E>(
    private val graphs: Collection<HierarchicalLeisersonCircuitGraph<G, N, E>>,
    private val expansionNodeFactory: () -> N,
    private val expansionEdgeValueFactory: () -> E,
) {
    data class SolveResult<G, N, E>(
        val retimedGraph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        val unretimedProperties: TimingProperties,
        val retimedProperties: TimingProperties,
    )

    private data class ChildExpansion<N>(
        val inputNode: WeightedGraph.Node<N>,
        val outputNode: WeightedGraph.Node<N>,
        val inputDelayNode: WeightedGraph.Node<N>?,
        val outputDelayNode: WeightedGraph.Node<N>?,
        val combinationalDelayNode: WeightedGraph.Node<N>?,
        val retimingDifference: Int,
    )

    fun solveAll(targetClockPeriod: Int): Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>> {
        val results = mutableMapOf<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>>()
        val processed = mutableSetOf<HierarchicalLeisersonCircuitGraph<G, N, E>>()

        fun processGraph(graph: HierarchicalLeisersonCircuitGraph<G, N, E>) {
            if (graph in processed) return
            graph.childGraphs().forEach { processGraph(it) }
            solveSingle(graph, results, targetClockPeriod)?.let { results[graph] = it }
            processed.add(graph)
        }

        graphs.forEach { processGraph(it) }
        return results
    }

    private fun computeTimingProperties(graph: LeisersonCircuitGraph<G, N, E>): TimingProperties {
        val inputNodes = graph.rootNodes()
        val outputNodes = graph.leafNodes()

        val fullPaths = inputNodes.flatMap { inputNode ->
            graph.findFastestConnectionsFromNode(inputNode)
                .filter { it.sink in outputNodes }
        }

        val registerDelay = fullPaths.minByOrNull { it.registerCount }?.registerCount ?: 0
        val combinationalDelay = fullPaths.filter { it.registerCount == 0 }.maxByOrNull { it.delay }?.delay

        val inputDelay = inputNodes.flatMap { inputNode ->
            graph.findFastestConnectionsFromNode(inputNode)
                .filter { it.sink !in outputNodes }
                .filter { it.registerCount == 0 }
                .map { it.delay }
        }.maxOrNull()

        val reversedGraph = LeisersonCircuitGraph(
            value = graph.value,
            nodes = graph.nodes,
            edges = graph.edges.map { WeightedGraph.Edge(it.weight, it.sink, it.source, it.value) },
        )

        val outputDelay = reversedGraph.rootNodes().flatMap { outputNode ->
            reversedGraph.findFastestConnectionsFromNode(outputNode)
                .filter { it.sink !in inputNodes }
                .filter { it.registerCount == 0 }
                .map { it.delay }
        }.maxOrNull()

        return TimingProperties(
            inputDelay = inputDelay,
            outputDelay = outputDelay,
            combinationalDelay = combinationalDelay,
            registerDelay = registerDelay,
            clockPeriod = graph.computeClockPeriod(),
            registerCount = graph.edges.sumOf { it.weight },
        )
    }

    private fun solveSingle(
        graph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        childResults: Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>>,
        targetClockPeriod: Int,
    ): SolveResult<G, N, E>? = Logger.run("Retiming hierarchical graph") {
        // Step 1: Flatten to LeisersonCircuitGraph

        // Map from hierarchical node to its flat counterpart (for non-ChildGraphNode nodes)
        val flatNodeByHierarchicalNode = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, WeightedGraph.Node<N>>()
        // Map from hierarchical child node to its expansion (keyed by Node<N> to avoid casts when looking up edge endpoints)
        val expansionByChildNode = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, ChildExpansion<N>>()
        val allFlatNodes = mutableListOf<WeightedGraph.Node<N>>()

        // Leaf and virtual nodes map directly to a single WeightedGraph.Node
        graph.nodes.forEach { hierarchicalNode ->
            val nodeValue: N = hierarchicalNode.value
            when (hierarchicalNode) {
                is HierarchicalLeisersonCircuitGraph.LeafNode<*> -> {
                    val flatNode = WeightedGraph.Node(hierarchicalNode.weight, nodeValue)
                    allFlatNodes.add(flatNode)
                    flatNodeByHierarchicalNode[hierarchicalNode] = flatNode
                }
                is HierarchicalLeisersonCircuitGraph.VirtualNode<*> -> {
                    val flatNode = WeightedGraph.Node(0, nodeValue)
                    allFlatNodes.add(flatNode)
                    flatNodeByHierarchicalNode[hierarchicalNode] = flatNode
                }
                is HierarchicalLeisersonCircuitGraph.ChildGraphNode<*, *, *> -> { /* handled below */ }
            }
        }

        // Child nodes expand into contracted subgraphs using the child's solve result
        if (graph.childNodes().any { it.childGraph !in childResults }) {
            Logger.error { "Missing child solve result — child was not processed first" }
            return@run null
        }

        graph.childNodes().forEach { childNode ->
            val childResult = childResults[childNode.childGraph]!!
            val retimedProps = childResult.retimedProperties
            val unretimedProps = childResult.unretimedProperties
            val retimingDifference = retimedProps.registerDelay - unretimedProps.registerDelay

            val expansionInputNode = WeightedGraph.Node<N>(0, expansionNodeFactory())
            val expansionOutputNode = WeightedGraph.Node<N>(0, expansionNodeFactory())
            allFlatNodes.add(expansionInputNode)
            allFlatNodes.add(expansionOutputNode)

            var inputDelayNode: WeightedGraph.Node<N>? = null
            var outputDelayNode: WeightedGraph.Node<N>? = null
            if (retimedProps.inputDelay != null && retimedProps.outputDelay != null) {
                inputDelayNode = WeightedGraph.Node(retimedProps.inputDelay, expansionNodeFactory())
                outputDelayNode = WeightedGraph.Node(retimedProps.outputDelay, expansionNodeFactory())
                allFlatNodes.add(inputDelayNode)
                allFlatNodes.add(outputDelayNode)
            }

            var combinationalDelayNode: WeightedGraph.Node<N>? = null
            if (retimedProps.combinationalDelay != null) {
                combinationalDelayNode = WeightedGraph.Node(retimedProps.combinationalDelay, expansionNodeFactory())
                allFlatNodes.add(combinationalDelayNode)
            }

            expansionByChildNode[childNode] = ChildExpansion(
                inputNode = expansionInputNode,
                outputNode = expansionOutputNode,
                inputDelayNode = inputDelayNode,
                outputDelayNode = outputDelayNode,
                combinationalDelayNode = combinationalDelayNode,
                retimingDifference = retimingDifference,
            )
        }

        // Build flat edges — expansion internal edges first, then hierarchical edges
        val allFlatEdges = mutableListOf<WeightedGraph.Edge<N, E>>()

        expansionByChildNode.values.forEach { expansion ->
            if (expansion.inputDelayNode != null && expansion.outputDelayNode != null) {
                allFlatEdges.add(WeightedGraph.Edge(0, expansion.inputNode, expansion.inputDelayNode, expansionEdgeValueFactory()))
                allFlatEdges.add(WeightedGraph.Edge(0, expansion.outputDelayNode, expansion.outputNode, expansionEdgeValueFactory()))
                allFlatEdges.add(WeightedGraph.Edge(-expansion.retimingDifference + 1, expansion.inputDelayNode, expansion.outputDelayNode, expansionEdgeValueFactory()))
            }
            if (expansion.combinationalDelayNode != null) {
                allFlatEdges.add(WeightedGraph.Edge(-expansion.retimingDifference, expansion.inputNode, expansion.combinationalDelayNode, expansionEdgeValueFactory()))
                allFlatEdges.add(WeightedGraph.Edge(0, expansion.combinationalDelayNode, expansion.outputNode, expansionEdgeValueFactory()))
            }
        }

        // Hierarchical edges follow expansion edges; track start index for back-mapping
        val hierarchicalEdges = graph.edges.toList()
        val flatEdgeStartIndex = allFlatEdges.size

        fun flatNodeFor(hierarchicalNode: HierarchicalLeisersonCircuitGraph.Node<N>, isSource: Boolean): WeightedGraph.Node<N> {
            flatNodeByHierarchicalNode[hierarchicalNode]?.let { return it }
            val expansion = expansionByChildNode[hierarchicalNode]!!
            return if (isSource) expansion.outputNode else expansion.inputNode
        }

        hierarchicalEdges.forEach { hEdge ->
            allFlatEdges.add(
                WeightedGraph.Edge(
                    weight = hEdge.weight,
                    source = flatNodeFor(hEdge.source, isSource = true),
                    sink = flatNodeFor(hEdge.sink, isSource = false),
                    value = hEdge.value,
                )
            )
        }

        val flatGraph = LeisersonCircuitGraph(graph.value, allFlatNodes, allFlatEdges)

        // Step 2: Unretimed timing properties
        val unretimedProperties = computeTimingProperties(flatGraph)

        // Step 3: Equality constraints for contracted subgraph nodes
        val equalityConstraints = mutableListOf<NodeEqualityConstraint<N>>()
        expansionByChildNode.values.forEach { expansion ->
            equalityConstraints.add(NodeEqualityConstraint(expansion.inputNode, expansion.outputNode, expansion.retimingDifference.toLong()))
            if (expansion.inputDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.inputNode, expansion.inputDelayNode, 0L))
            }
            if (expansion.outputDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.outputNode, expansion.outputDelayNode, 0L))
            }
            if (expansion.combinationalDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.outputNode, expansion.combinationalDelayNode, 0L))
            }
        }

        // Step 4: Run the flat solver
        val retimedFlatGraph = MinimalRegisterSolver(flatGraph, equalityConstraints).solveOrNull(targetClockPeriod)
            ?: return@run null

        // Step 5: Back-map retimed edge weights to the hierarchical graph
        // Flat edges are ordered: expansion edges [0, flatEdgeStartIndex), then hierarchical edges [flatEdgeStartIndex, ...)
        val retimedFlatEdgeList = retimedFlatGraph.edges.toList()

        // Point each ChildGraphNode at its own (already-solved) retimed graph, so the graph returned here is
        // self-consistent and safe to flatten(). ChildGraphNode is a data class keyed in part by childGraph, so
        // both the node list and every edge endpoint that touches a child must be swapped together — leaving edges
        // pointing at the old node would desync nodes/edges (breaking rootNodes()/leafNodes() and flatten()).
        val retimedChildNodeByOriginal = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, HierarchicalLeisersonCircuitGraph.ChildGraphNode<G, N, E>>()
        graph.childNodes().forEach { childNode ->
            retimedChildNodeByOriginal[childNode] = childNode.copy(
                childGraph = childResults.getValue(childNode.childGraph).retimedGraph,
            )
        }

        fun retimedNodeFor(node: HierarchicalLeisersonCircuitGraph.Node<N>) = retimedChildNodeByOriginal[node] ?: node

        val retimedHierarchicalEdges = hierarchicalEdges.mapIndexed { hIdx, hEdge ->
            hEdge.copy(
                weight = retimedFlatEdgeList[flatEdgeStartIndex + hIdx].weight,
                source = retimedNodeFor(hEdge.source),
                sink = retimedNodeFor(hEdge.sink),
            )
        }

        val retimedGraph = HierarchicalLeisersonCircuitGraph(
            value = graph.value,
            nodes = graph.nodes.map { retimedNodeFor(it) },
            edges = retimedHierarchicalEdges,
        )

        // Step 6: Retimed timing properties
        val retimedProperties = computeTimingProperties(retimedFlatGraph)

        SolveResult(retimedGraph, unretimedProperties, retimedProperties)
    }
}
