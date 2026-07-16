package com.uabutler.netlistir.transformer.util

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualBodyNode
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.util.Logger
import com.uabutler.util.PropagationDelay
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import com.uabutler.util.graph.WeightedGraph.Edge
import com.uabutler.util.graph.util.HierarchicalMinimalRegisterSolver
import com.uabutler.verilogir.builder.creator.util.Identifier

class HierarchicalRetimer(
    val modules: Collection<MutableModule>,
) {

    data class GraphStats(
        val inputDelay: Int?,
        val outputDelay: Int?,
        val combinationalDelay: Int?,
        val registerDelay: Int,
        val clockPeriod: Int,
        val registerCount: Int,
    )

    private val unretimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()
    private val retimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()

    private fun <G, N, E> graphStats(graph: HierarchicalLeisersonCircuitGraph<G, N, E>): GraphStats {
        val inputNodes = graph.rootNodes()
        val outputNodes = graph.leafNodes()

        val fullPaths = inputNodes.flatMap { inputNode ->
            graph.findFastestConnectionsFromNode(inputNode)
                .filter { it.sink in outputNodes }
        }

        val registerDelayPath = fullPaths.minBy{ it.registerCount }

        Logger.trace { "Register delay path: $registerDelayPath" }

        val registerDelay = registerDelayPath.registerCount

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
            edges = graph.edges.map {
                Edge(
                    source = it.sink,
                    sink = it.source,
                    weight = it.weight,
                    value = it.value,
                )
            },
        )

        val outputDelay = reversedGraph.rootNodes().flatMap { outputNode ->
            reversedGraph.findFastestConnectionsFromNode(outputNode)
                .filter { it.sink !in inputNodes }
                .filter { it.registerCount == 0 }
                .map { it.delay }
        }.maxOrNull()

        val clockPeriod = graph.computeClockPeriod()

        val registerCount = graph.edges.sumOf { it.weight }

        return GraphStats(
            inputDelay = inputDelay,
            outputDelay = outputDelay,
            combinationalDelay = combinationalDelay,
            registerDelay = registerDelay,
            clockPeriod = clockPeriod,
            registerCount = registerCount,
        )
    }

    private fun toHierarchical(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>): HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>> {
        Logger.start("Converting ${Identifier.module(graph.value.invocation)} hierarchical graph", Logger.Level.TRACE)
        val currentEdgeSet = graph.edges.toMutableSet()
        val currentNodeSet = graph.nodes.toMutableSet()
        val currentContractCircuitGraphSet = mutableSetOf<HierarchicalLeisersonCircuitGraph.ContractCircuitGraph<Node, Collection<NonRegisterConnection>>>()

        val invocationNodes = currentNodeSet.filter { it.value is ModuleInvocationNode }
        Logger.trace { "Invocation nodes: ${invocationNodes.size}" }

        invocationNodes.forEach { invocationNode ->
            val moduleInvocationNode = invocationNode.value as ModuleInvocationNode
            Logger.trace { "Removing ${Identifier.module(moduleInvocationNode.invocation)} from graph" }
            val oldIncomingEdges = currentEdgeSet.filter { it.sink == invocationNode }
            val oldOutgoingEdges = currentEdgeSet.filter { it.source == invocationNode }

            val unretimedModuleStats = unretimedGraphStats[moduleInvocationNode.invocation]!!
            val retimedModuleStats = retimedGraphStats[moduleInvocationNode.invocation]!!

            val retimingDifference = retimedModuleStats.registerDelay - unretimedModuleStats.registerDelay

            Logger.trace { "Input delay: ${retimedModuleStats.inputDelay}" }
            Logger.trace { "Output delay: ${retimedModuleStats.outputDelay}" }
            Logger.trace { "Register delay: ${unretimedModuleStats.registerDelay}" }
            Logger.trace { "Retimed Register delay: ${retimedModuleStats.registerDelay}" }

            val newInputNode = WeightedGraph.Node(
                weight = 0,
                value = VirtualBodyNode(
                    identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+input",
                    parentModule = invocationNode.value.parentModule,
                ) as Node
            )

            val newOutputNode = WeightedGraph.Node(
                weight = 0,
                value = VirtualBodyNode(
                    identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+output",
                    parentModule = invocationNode.value.parentModule,
                ) as Node
            )

            // Register Path
            var inputDelayNode: WeightedGraph.Node<Node>? = null
            var outputDelayNode: WeightedGraph.Node<Node>? = null

            var inputDelayEdge: Edge<Node, Collection<NonRegisterConnection>>? = null
            var outputDelayEdge: Edge<Node, Collection<NonRegisterConnection>>? = null
            var registerDelayEdge: Edge<Node, Collection<NonRegisterConnection>>? = null

            if (retimedModuleStats.inputDelay != null && retimedModuleStats.outputDelay != null) {
                inputDelayNode = WeightedGraph.Node(
                    weight = retimedModuleStats.inputDelay,
                    value = VirtualBodyNode(
                        identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+input-delay-node",
                        parentModule = invocationNode.value.parentModule,
                    ) as Node
                )

                outputDelayNode = WeightedGraph.Node(
                    weight = retimedModuleStats.outputDelay,
                    value = VirtualBodyNode(
                        identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+output-delay-node",
                        parentModule = invocationNode.value.parentModule,
                    ) as Node
                )

                inputDelayEdge = Edge(
                    source = newInputNode,
                    sink = inputDelayNode,
                    weight = 0,
                    value = emptyList(),
                )

                outputDelayEdge = Edge(
                    source = outputDelayNode,
                    sink = newOutputNode,
                    weight = 0,
                    value = emptyList(),
                )

                registerDelayEdge = Edge(
                    source = inputDelayNode,
                    sink = outputDelayNode,
                    weight = -retimingDifference + 1,
                    value = emptyList(),
                )
            }

            // Combinational Path
            var combinationalDelayNode: WeightedGraph.Node<Node>? = null

            var combinationalInputDelayEdge: Edge<Node, Collection<NonRegisterConnection>>? = null
            var combinationalOutputDelayEdge: Edge<Node, Collection<NonRegisterConnection>>? = null

            if (retimedModuleStats.combinationalDelay != null) {
                combinationalDelayNode = WeightedGraph.Node(
                    weight = retimedModuleStats.combinationalDelay,
                    value = VirtualBodyNode(
                        identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+combinational-node",
                        parentModule = invocationNode.value.parentModule,
                    ) as Node
                )

                combinationalInputDelayEdge = Edge(
                    source = newInputNode,
                    sink = combinationalDelayNode,
                    weight = -retimingDifference,
                    value = emptyList(),
                )

                combinationalOutputDelayEdge = Edge(
                    source = combinationalDelayNode,
                    sink = newOutputNode,
                    weight = 0,
                    value = emptyList(),
                )
            }

            // Incoming and Outgoing Edges
            val newIncomingEdges = oldIncomingEdges.map { oldIncomingEdge ->
                val newSource = if (oldIncomingEdge.source == invocationNode) newOutputNode else oldIncomingEdge.source

                Edge(
                    source = newSource,
                    sink = newInputNode,
                    weight = oldIncomingEdge.weight,
                    value = oldIncomingEdge.value,
                )
            }

            val newOutgoingEdges = oldOutgoingEdges.map { oldOutgoingEdge ->
                val newSink = if (oldOutgoingEdge.sink == invocationNode) newInputNode else oldOutgoingEdge.sink

                Edge(
                    source = newOutputNode,
                    sink = newSink,
                    weight = oldOutgoingEdge.weight,
                    value = oldOutgoingEdge.value,
                )
            }

            val contractCircuitGraph = HierarchicalLeisersonCircuitGraph.ContractCircuitGraph(
                retimedInputDelay = retimedModuleStats.inputDelay,
                retimedOutputDelay = retimedModuleStats.outputDelay,
                retimedCombinationalDelay = retimedModuleStats.combinationalDelay,

                unretimedRegisterDelay = unretimedModuleStats.registerDelay,
                retimedRegisterDelay = retimedModuleStats.registerDelay,

                originalIncomingEdges = oldIncomingEdges,
                originalOutgoingEdges = oldOutgoingEdges,

                contractedIncomingEdges = newIncomingEdges,
                contractedOutgoingEdges = newOutgoingEdges,

                originalNode = invocationNode,

                contractedInputNode = newInputNode,
                contractedOutputNode = newOutputNode,

                contractedInputDelayNode = inputDelayNode,
                contractedOutputDelayNode = outputDelayNode,

                contractedInputDelayEdge = inputDelayEdge,
                contractedOutputDelayEdge = outputDelayEdge,
                contractedRegisterDelayEdge = registerDelayEdge,

                contractedCombinationalDelayNode = combinationalDelayNode,

                contractedCombinationalDelayInputEdge = combinationalInputDelayEdge,
                contractedCombinationalDelayOutputEdge = combinationalOutputDelayEdge,
            )

            // Remove the old graph
            currentNodeSet.remove(invocationNode)
            currentEdgeSet.removeAll(oldIncomingEdges.toSet())
            currentEdgeSet.removeAll(oldOutgoingEdges.toSet())

            // Add the contract graph
            contractCircuitGraph.contractedGraphNodes().forEach { currentNodeSet.add(it) }
            contractCircuitGraph.contractedGraphEdges().forEach { currentEdgeSet.add(it) }

            // Add the connections to the new contract graph
            currentEdgeSet.addAll(newIncomingEdges)
            currentEdgeSet.addAll(newOutgoingEdges)

            // Save the contract graph
            currentContractCircuitGraphSet.add(contractCircuitGraph)
        }

        return HierarchicalLeisersonCircuitGraph(
            value = graph.value,
            nodes = currentNodeSet,
            edges = currentEdgeSet,
            contractCircuitGraphs = currentContractCircuitGraphSet,
        ).also { Logger.finish() }
    }

    private fun fromHierarchical(graph: HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>): LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>> {
        val currentEdgeSet = graph.edges.toMutableSet()
        val currentNodeSet = graph.nodes.toMutableSet()

        graph.contractCircuitGraphs.forEach { contractCircuitGraph ->
            // Remove the old graph
            contractCircuitGraph.contractedGraphNodes().forEach { currentNodeSet.remove(it) }
            contractCircuitGraph.contractedGraphEdges().forEach { currentEdgeSet.remove(it) }

            // Unhook from graph
            currentEdgeSet.removeAll(contractCircuitGraph.contractedIncomingEdges.toSet())
            currentEdgeSet.removeAll(contractCircuitGraph.contractedOutgoingEdges.toSet())

            // Add the new graph
            currentNodeSet.add(contractCircuitGraph.originalNode)
            currentEdgeSet.addAll(contractCircuitGraph.originalIncomingEdges)
            currentEdgeSet.addAll(contractCircuitGraph.originalOutgoingEdges)
        }

        return LeisersonCircuitGraph(
            value = graph.value,
            nodes = currentNodeSet,
            edges = currentEdgeSet,
        )
    }

    private fun printGraph(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>) = buildString {
        println("PRINTING GRAPH:")
        println("  Nodes:")
        graph.nodes.forEach { node ->
            println("    ${node.weight}: ${node.value.name()} [${node.value.nodeType()}]")
        }
        println("  Edges:")
        graph.edges.forEach { edge ->
            println("    ${edge.weight}: ${edge.source.value.name()} -> ${edge.sink.value.name()}")
        }
    }

    private fun printAllGraphStats(retimeOrder: List<Module.Invocation>) {
        retimeOrder.forEach { invocation ->
            Logger.start("${invocation.gaplFunctionName} retiming analysis", Logger.Level.INFO)

            val unretimedStats = unretimedGraphStats[invocation]
            val retimedStats = retimedGraphStats[invocation]

            if (unretimedStats != null && retimedStats != null) {
                Logger.start("Unretimed", Logger.Level.INFO)
                Logger.info { "Clock Period:   ${unretimedStats.clockPeriod}" }
                Logger.info { "Register Count: ${unretimedStats.registerCount}" }
                Logger.info { "Register Delay: ${unretimedStats.registerDelay}" }
                Logger.finish()

                Logger.start("Retimed", Logger.Level.INFO)
                Logger.info { "Clock Period:   ${retimedStats.clockPeriod}" }
                Logger.info { "Register Count: ${retimedStats.registerCount}" }
                Logger.info { "Register Delay: ${retimedStats.registerDelay}" }
                Logger.finish()

                Logger.info { "Clock Period Decrease:   ${unretimedStats.clockPeriod - retimedStats.clockPeriod}" }
                Logger.info { "Register Count Increase: ${retimedStats.registerCount - unretimedStats.registerCount}" }
                Logger.info { "Register Delay Increase: ${retimedStats.registerDelay - unretimedStats.registerDelay}" }
            } else {
                Logger.error { "No stats for ${invocation.gaplFunctionName}" }
            }

            Logger.finish()
        }
    }

    fun retimeAll(propagationDelay: PropagationDelay, targetClockPeriod: Int): List<MutableModule> {
        val baseGraph = modules
            .map { NetlistLeisersonCircuitConverter.fromModule(it, propagationDelay, false) }
            .associateBy { it.value.invocation }

        val retimeOrder = InvocationGraph(modules).topologicalSort().map { it.invocation }.reversed()

        return retimeOrder.map { invocation ->
            Logger.start("Retiming module ${Identifier.module(invocation)}")
            val unretimedNonhierarchicalGraph = baseGraph[invocation]!!
            val unretimedHierarchicalGraph = toHierarchical(unretimedNonhierarchicalGraph)

            unretimedGraphStats[invocation] = graphStats(unretimedHierarchicalGraph)

            val retimedBaseGraph = HierarchicalMinimalRegisterSolver(unretimedHierarchicalGraph).solveOrNull(targetClockPeriod)!!

            // TODO: Gross, hacky
            val retimedHierarchicalLeisersonCircuitGraph = HierarchicalLeisersonCircuitGraph(
                value = retimedBaseGraph.value,
                nodes = retimedBaseGraph.nodes,
                edges = retimedBaseGraph.edges,
                contractCircuitGraphs = unretimedHierarchicalGraph.contractCircuitGraphs,
            )

            retimedGraphStats[invocation] = graphStats(retimedHierarchicalLeisersonCircuitGraph)

            val retimedNonhierarchicalGraph = fromHierarchical(retimedHierarchicalLeisersonCircuitGraph)
            NetlistLeisersonCircuitConverter.toModule(retimedNonhierarchicalGraph).also { Logger.finish() }
        }.also {
            printAllGraphStats(retimeOrder)
        }
    }

}