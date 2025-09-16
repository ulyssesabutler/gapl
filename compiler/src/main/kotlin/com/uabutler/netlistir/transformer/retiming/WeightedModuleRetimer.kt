package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.transformer.retiming.graph.AdjacencyList
import com.uabutler.netlistir.transformer.retiming.graph.ModuleAdjacencyList
import com.uabutler.netlistir.transformer.retiming.graph.WeightedModule

object WeightedModuleRetimer {

    // Algorithm CP
    private fun computeCombinationalDelay(graph: ModuleAdjacencyList): Map<AdjacencyList.Node, Int> {
        val combinationalSubgraph = graph.subgraph(
            edgeFilter = { it.weight == 0 }
        )

        val sortedCombinationalSubgraphNodes = combinationalSubgraph.topologicalSort()

        data class NodeData(val node: AdjacencyList.Node, val incomingEdges: MutableList<AdjacencyList.Edge>, var combinationalDelay: Int)
        val nodeData = sortedCombinationalSubgraphNodes.associateWith { NodeData(it, mutableListOf(), 0) }

        combinationalSubgraph.edges.forEach { edge -> nodeData[edge.sink]!!.incomingEdges.add(edge) }

        sortedCombinationalSubgraphNodes.forEach { node ->
            val currentNodeData = nodeData[node]!!
            val incomingEdges = currentNodeData.incomingEdges

            if (incomingEdges.isEmpty()) {
                currentNodeData.combinationalDelay = node.weight
            } else {
                val maxIncomingDelay = incomingEdges.maxOf { nodeData[it.source]!!.combinationalDelay }
                currentNodeData.combinationalDelay = maxIncomingDelay + node.weight
            }
        }

        return nodeData.mapValues { it.value.combinationalDelay }
    }

    private fun computeClockPeriod(graph: ModuleAdjacencyList): Int {
        return computeCombinationalDelay(graph).values.max()
    }

    // Algorithm WD

    // Special case of Algorithm WD: Only returns unique Delays
    private fun computePossibleClockPeriods(graph: ModuleAdjacencyList): List<Int> {
        val possibleClockPeriods = mutableSetOf<Int>()

        graph.nodes.forEach { node ->
            val possibleClockPeriodsFromNode = graph.dijkstra(
                source = node,
                edgeWeight = { it.weight to -it.source.weight },
                edgeComparator = compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
                weightAddition = { a, b -> a.first + b.first to a.second + b.second }
            ).values.map { -it.second }

            possibleClockPeriods.addAll(possibleClockPeriodsFromNode)
        }

        return possibleClockPeriods.toList()
    }

    private fun attemptRetiming(graph: ModuleAdjacencyList, clockPeriod: Int): ModuleAdjacencyList.Retiming? {
        val currentRetiming = ModuleAdjacencyList.Retiming(graph, graph.nodes.associateWith { 0 })
        val originalNodes = graph.nodes.associateBy { it.underlyingNode.node }

        repeat(graph.nodes.size - 1) {
            computeCombinationalDelay(currentRetiming.retimedModule()).forEach { (node, delay) ->
                if (delay > clockPeriod) {
                    val nodeInRetiming = originalNodes[node.underlyingNode.node]!!
                    currentRetiming.forNode(nodeInRetiming, currentRetiming.ofNode(nodeInRetiming) + 1)
                }
            }
        }

        val clockPeriodOfRetimedGraph = computeClockPeriod(currentRetiming.retimedModule())
        return if (clockPeriodOfRetimedGraph <= clockPeriod) currentRetiming else null
    }

    fun retime(module: WeightedModule): WeightedModule {
        val adjacencyList = ModuleAdjacencyList(module)
        val clockPeriods = computePossibleClockPeriods(adjacencyList).distinct().sorted()

        // Cache attempt results to avoid recomputation during binary search
        val cache = HashMap<Int, ModuleAdjacencyList.Retiming?>()
        fun attempt(value: Int): ModuleAdjacencyList.Retiming? = cache.getOrPut(value) { attemptRetiming(adjacencyList, value) }

        // Populate only the necessary cache entries
        clockPeriods.binarySearch { value ->
            if (attempt(value) == null) -1 else 1
        }

        // Now, return the smallest clock period in the cache that yielded a valid retiming
        val retiming = cache
            .filter { it.value != null }
            .minBy { it.key }
            .value!!

        return retiming.retimedModule().weightedModule
    }

}