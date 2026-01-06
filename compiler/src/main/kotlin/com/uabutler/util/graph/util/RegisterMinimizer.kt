package com.uabutler.util.graph.util

import com.uabutler.util.graph.LeisersonCircuitGraph
import com.google.ortools.Loader
import com.google.ortools.graph.MinCostFlow
import com.google.ortools.graph.MinCostFlowBase
import com.uabutler.util.graph.WeightedGraph
import kotlin.math.abs

class RegisterMinimizer<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming<G, N, E>(graph) {

    companion object {

        init { Loader.loadNativeLibraries() }

        private data class SuperNodes(
            val sourceIndex: Int,
            val sinkIndex: Int,
            val capacity: Long,
        )

        private data class NodeStats(
            val index: Int,
            val fanIn: Int,
            val fanOut: Int,
            val cost: Int,
        )

        private data class FlowNetwork<N, E>(
            val flowNetwork: MinCostFlow,
            val superNodes: SuperNodes,
            val circuitEdges: Map<WeightedGraph.Edge<N, E>, Int>,
            val capacityEdges: List<Int>,
            val periodEdges: List<Int>,
            val nodes: Map<WeightedGraph.Node<N>, NodeStats>,
        ) {
            fun getEdgeIndices(): List<Int> = circuitEdges.values.toList() + capacityEdges + periodEdges
            fun getEdgeCount(): Int = getEdgeIndices().size
            fun getNodeIndices(): List<Int> = nodes.values.map { it.index } + listOf(superNodes.sourceIndex, superNodes.sinkIndex)
            fun getNodeCount(): Int = getNodeIndices().size
        }

        private fun MinCostFlow.createCircuitNodesFromLeisersonCircuitGraph(graph: LeisersonCircuitGraph<*, *, *>): Map<WeightedGraph.Node<*>, Int> {
            val nodes = graph.nodes.mapIndexed { index, node ->
                this.setNodeSupply(index, 0)
                node to index
            }

            return nodes.toMap()
        }

        private fun <G, N, E> getNodeStats(graph: LeisersonCircuitGraph<G, N, E>, nodes: Map<WeightedGraph.Node<*>, Int>): Map<WeightedGraph.Node<N>, NodeStats> {
            // Fan-in and fan-out
            val incomingEdges = graph.edges.groupBy { it.sink }
            val outgoingEdges = graph.edges.groupBy { it.source }

            val fanIn = graph.nodes.associateWith { incomingEdges[it]!!.size }
            val fanOut = graph.nodes.associateWith { outgoingEdges[it]!!.size }

            // Use the fan-in and fan-out to compute the number of registers that would need to be removed from the egress to the ingress when retiming this node
            return graph.nodes.associateWith { node ->
                NodeStats(
                    index = nodes[node]!!,
                    fanIn = fanIn[node]!!,
                    fanOut = fanOut[node]!!,
                    cost = fanIn[node]!! - fanOut[node]!!,
                )
            }
        }

        private fun <N> MinCostFlow.createSuperNodes(circuitNodes: Map<WeightedGraph.Node<N>, NodeStats>): SuperNodes {
            val largestCircuitNodeIndex = circuitNodes.values.maxOf { it.index }

            val superSourceIndex = largestCircuitNodeIndex + 1
            val superSinkIndex = largestCircuitNodeIndex + 2

            val totalCapacity = circuitNodes.values.filter { it.cost > 0 }.sumOf { it.cost }.toLong()

            this.setNodeSupply(superSourceIndex, totalCapacity)
            this.setNodeSupply(superSinkIndex, -totalCapacity)

            return SuperNodes(superSourceIndex, superSinkIndex, totalCapacity)
        }

        private fun <G, N ,E> MinCostFlow.createCircuitEdges(
            graph: LeisersonCircuitGraph<G, N, E>,
            circuitNodes: Map<WeightedGraph.Node<N>, NodeStats>,
            capacity: Long,
        ): Map<WeightedGraph.Edge<N, E>, Int> {
            return graph.edges.associateWith { edge ->
                val sourceIndex = circuitNodes[edge.source]!!.index
                val sinkIndex = circuitNodes[edge.sink]!!.index
                val cost = edge.weight

                this.addArcWithCapacityAndUnitCost(sourceIndex, sinkIndex, capacity, cost.toLong())
            }
        }

        private fun <N> MinCostFlow.createCapacityEdges(
            circuitNodes: Map<WeightedGraph.Node<N>, NodeStats>,
            superNodes: SuperNodes,
        ): List<Int> {
            return circuitNodes.map { (_, stats) ->
                if (stats.cost > 0) {
                    this.addArcWithCapacityAndUnitCost(superNodes.sourceIndex, stats.index, abs(stats.cost.toLong()), 0)
                } else if (stats.cost < 0) {
                    this.addArcWithCapacityAndUnitCost(stats.index, superNodes.sinkIndex, abs(stats.cost.toLong()), 0)
                } else {
                    null
                }
            }.filterNotNull()
        }

        private fun <G, N, E> MinCostFlow.createPeriodEdges(
            graph: LeisersonCircuitGraph<G, N, E>,
            circuitNodes: Map<WeightedGraph.Node<N>, NodeStats>,
            targetClockPeriod: Int,
            capacity: Long,
        ): List<Int> {
            val periodEdgeIndices = mutableListOf<Int>()

            graph.nodes.forEach { sourceNode ->
                val sourceNodeIndex = circuitNodes[sourceNode]!!.index
                graph.findFastestConnectionsFromNode(sourceNode).forEach { connection ->
                    if (connection.delay > targetClockPeriod) {
                        val sinkNodeIndex = circuitNodes[connection.sink]!!.index
                        val registerCount = connection.registerCount.toLong()

                        val index = this.addArcWithCapacityAndUnitCost(sourceNodeIndex, sinkNodeIndex, capacity, registerCount - 1)
                        periodEdgeIndices.add(index)
                    }
                }
            }

            return periodEdgeIndices
        }

        private fun <G, N, E> constructFlowNetworkFromCircuitGraph(
            graph: LeisersonCircuitGraph<G, N, E>,
            targetClockPeriod: Int,
        ): FlowNetwork<N, E> {
            // "Infinity" capacity = Big-M. Must be >= total flow you might send.
            val infiniteCapacity = 1_000_000L
            val minCostFlow = MinCostFlow()

            val circuitNodeIndices = minCostFlow.createCircuitNodesFromLeisersonCircuitGraph(graph)
            val circuitNodes = getNodeStats(graph, circuitNodeIndices)
            val superNodes = minCostFlow.createSuperNodes(circuitNodes)

            val circuitEdges = minCostFlow.createCircuitEdges(graph, circuitNodes, infiniteCapacity)
            val capacityEdges = minCostFlow.createCapacityEdges(circuitNodes, superNodes)
            val periodEdges = minCostFlow.createPeriodEdges(graph, circuitNodes, targetClockPeriod, infiniteCapacity)

            return FlowNetwork(
                flowNetwork = minCostFlow,
                superNodes = superNodes,
                circuitEdges = circuitEdges,
                capacityEdges = capacityEdges,
                periodEdges = periodEdges,
                nodes = circuitNodes,
            )
        }

        private data class ResidualArc(val sourceIndex: Int, val sinkIndex: Int, val cost: Long)

        private fun recoverPotentialsFromOptimalFlow(flowNetwork: FlowNetwork<*, *>): Map<Int, Long> {
            val minCostFlow = flowNetwork.flowNetwork
            val residualEdges = mutableListOf<ResidualArc>()

            flowNetwork.getEdgeIndices().forEach { edgeIndex ->
                val sourceIndex = minCostFlow.getTail(edgeIndex)
                val sinkIndex = minCostFlow.getHead(edgeIndex)
                val capacity = minCostFlow.getCapacity(edgeIndex)
                val flow = minCostFlow.getFlow(edgeIndex)
                val cost = minCostFlow.getUnitCost(edgeIndex)

                // forward residual if not saturated
                if (flow < capacity) residualEdges.add(ResidualArc(sourceIndex, sinkIndex, cost))
                // backward residual if positive flow
                if (flow > 0L) residualEdges.add(ResidualArc(sourceIndex, sinkIndex, -cost))
            }

            // Potentials p[v]. We can start them all at 0 because potentials are defined up to a constant.
            val potentials = flowNetwork.getNodeIndices().associateWith { 0L }.toMutableMap()

            // Relax constraints: p[to] <= p[from] + cost
            // This is Bellman-Ford for difference constraints.
            repeat(flowNetwork.getNodeCount() - 1) {
                var changed = false
                for (residualEdge in residualEdges) {
                    val candidate = potentials[residualEdge.sourceIndex]!! + residualEdge.cost
                    if (candidate < potentials[residualEdge.sinkIndex]!!) {
                        potentials[residualEdge.sinkIndex] = candidate
                        changed = true
                    }
                }
                if (!changed) return@repeat
            }

            return potentials
        }

        private fun <G, N, E> deriveRetimingLabelsFromMinCostFlowSolution(
            graph: LeisersonCircuitGraph<G, N, E>,
            flowNetwork: FlowNetwork<N, E>,
        ): Map<WeightedGraph.Node<N>, Int> {
            val potentials = recoverPotentialsFromOptimalFlow(flowNetwork)
            val baseConstant = potentials.values.min()

            return graph.nodes.associateWith { node ->
                val nodeIndex = flowNetwork.nodes[node]!!.index
                val nodePotential = potentials[nodeIndex]!!
                (nodePotential - baseConstant).toInt()
            }
        }

        fun <G, N, E> minimizeRegisters(graph: LeisersonCircuitGraph<G, N, E>, targetClockPeriod: Int): LeisersonCircuitGraph<G, N, E> {
            val flowNetwork = constructFlowNetworkFromCircuitGraph(graph, targetClockPeriod)
            val retiming = RegisterMinimizer(graph)

            val status = flowNetwork.flowNetwork.solve()
            if (status != MinCostFlowBase.Status.OPTIMAL) {
                error("Min-cost flow failed. Status=$status")
            }

            val retimingLabels = deriveRetimingLabelsFromMinCostFlowSolution(graph, flowNetwork)

            // First, try the positive values
            retimingLabels.forEach { (node, label) -> retiming.setNodeLag(node, label) }
            if (graph.edges.all { retiming.getEdgeRegisterCount(it) >= 0 }) {
                return retiming.generateNewCircuit()
            }

            // Second, try the negative values
            retimingLabels.forEach { (node, label) -> retiming.setNodeLag(node, -label) }
            if (graph.edges.all { retiming.getEdgeRegisterCount(it) >= 0 }) {
                return retiming.generateNewCircuit()
            }

            // If those failed, there's a bug in the code
            throw IllegalStateException("Failed to find a valid retiming")
        }

    }

}