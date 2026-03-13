package com.uabutler.netlistir.transformer.util

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.netlist.VirtualBodyNode
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.util.Logger
import com.uabutler.util.PropagationDelay
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import com.uabutler.util.graph.util.HierarchicalMinimalRegisterSolver
import com.uabutler.verilogir.builder.creator.util.Identifier

class HierarchicalRetimer(
    val modules: Collection<Module>,
) {

    data class GraphStats(
        val inputDelay: Int,
        val outputDelay: Int,
        val registerDelay: Int,
    )

    private val unretimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()
    private val retimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()

    private fun <G, N, E> graphStats(graph: HierarchicalLeisersonCircuitGraph<G, N, E>): GraphStats {
        val inputNodes = graph.rootNodes()
        val outputNodes = graph.leafNodes()

        val registerDelayPath = inputNodes.flatMap { inputNode ->
            graph.findFastestConnectionsFromNode(inputNode)
                .filter { it.sink in outputNodes }
        }.minBy { it.registerCount }

        Logger.trace { "Register delay path: $registerDelayPath" }

        val registerDelay = registerDelayPath.registerCount

        val inputDelay = inputNodes.flatMap { inputNode ->
            graph.findFastestConnectionsFromNode(inputNode)
                .filter { it.registerCount == 0 }
                .map { it.delay }
        }.max()

        val reversedGraph = LeisersonCircuitGraph(
            value = graph.value,
            nodes = graph.nodes,
            edges = graph.edges.map {
                WeightedGraph.Edge(
                    source = it.sink,
                    sink = it.source,
                    weight = it.weight,
                    value = it.value,
                )
            },
        )

        val longestOutputDelay = reversedGraph.rootNodes().flatMap { outputNode ->
            reversedGraph.findFastestConnectionsFromNode(outputNode)
                .filter { it.registerCount == 0 }
        }.maxBy { it.delay }

        val outputDelay = if (registerDelay == 0) 0 else longestOutputDelay.delay

        return GraphStats(inputDelay, outputDelay, registerDelay)
    }

    private fun toHierarchical(graph: LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>): HierarchicalLeisersonCircuitGraph<Module, Node, Collection<NetlistLeisersonCircuitConverter.NonRegisterConnection>> {
        Logger.start("Converting ${Identifier.module(graph.value.invocation)} hierarchical graph", Logger.Level.TRACE)
        val currentEdgeSet = graph.edges.toMutableSet()
        val currentNodeSet = graph.nodes.toMutableSet()
        val currentContractCircuitGraphSet = mutableSetOf<HierarchicalLeisersonCircuitGraph.ContractCircuitGraph<Node, Collection<NetlistLeisersonCircuitConverter.NonRegisterConnection>>>()

        val invocationNodes = currentNodeSet.filter { it.value is ModuleInvocationNode }
        Logger.trace { "Invocation nodes: ${invocationNodes.size}" }

        invocationNodes.forEach { invocationNode ->
            val moduleInvocationNode = invocationNode.value as ModuleInvocationNode
            Logger.trace { "Removing ${Identifier.module(moduleInvocationNode.invocation)} from graph" }
            val oldIncomingEdges = currentEdgeSet.filter { it.sink == invocationNode }
            val oldOutgoingEdges = currentEdgeSet.filter { it.source == invocationNode }

            val unretimedModuleStats = unretimedGraphStats[moduleInvocationNode.invocation]!!
            val retimedModuleStats = retimedGraphStats[moduleInvocationNode.invocation]!!

            Logger.trace { "Input delay: ${retimedModuleStats.inputDelay}" }
            Logger.trace { "Output delay: ${retimedModuleStats.outputDelay}" }
            Logger.trace { "Register delay: ${unretimedModuleStats.registerDelay}" }
            Logger.trace { "Retimed Register delay: ${retimedModuleStats.registerDelay}" }

            val newInputNode = WeightedGraph.Node(
                weight = retimedModuleStats.inputDelay,
                value = VirtualBodyNode(
                    identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+input",
                    parentModule = invocationNode.value.parentModule,
                ) as Node
            )

            val newOutputNode = WeightedGraph.Node(
                weight = retimedModuleStats.outputDelay,
                value = VirtualBodyNode(
                    identifier = "${invocationNode.value.name()}+${invocationNode.value.invocation.gaplFunctionName}+output",
                    parentModule = invocationNode.value.parentModule,
                ) as Node
            )

            val registerEdge = WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>(
                source = newInputNode,
                sink = newOutputNode,
                weight = retimedModuleStats.registerDelay,
                value = emptyList(),
            )

            val contractCircuitGraph = HierarchicalLeisersonCircuitGraph.ContractCircuitGraph(
                moduleInvocationNode = moduleInvocationNode,
                retimedInputDelay = retimedModuleStats.inputDelay,
                retimedOutputDelay = retimedModuleStats.outputDelay,
                unretimedRegisterDelay = unretimedModuleStats.registerDelay,
                retimedRegisterDelay = retimedModuleStats.registerDelay,
                inputNode = newInputNode,
                outputNode = newOutputNode,
                edge = registerEdge,
            )

            val newIncomingEdges = oldIncomingEdges.map { oldIncomingEdge ->
                WeightedGraph.Edge(
                    source = oldIncomingEdge.source,
                    sink = newInputNode,
                    weight = oldIncomingEdge.weight,
                    value = oldIncomingEdge.value,
                )
            }

            val newOutgoingEdges = oldOutgoingEdges.map { oldOutgoingEdge ->
                WeightedGraph.Edge(
                    source = newOutputNode,
                    sink = oldOutgoingEdge.sink,
                    weight = oldOutgoingEdge.weight,
                    value = oldOutgoingEdge.value,
                )
            }

            // Remove the old graph
            currentNodeSet.remove(invocationNode)
            currentEdgeSet.removeAll(oldIncomingEdges.toSet())
            currentEdgeSet.removeAll(oldOutgoingEdges.toSet())

            // Add the contract graph
            currentNodeSet.add(newInputNode)
            currentNodeSet.add(newOutputNode)
            currentEdgeSet.add(registerEdge)

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

    private fun fromHierarchical(graph: HierarchicalLeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>): LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>> {
        val currentEdgeSet = graph.edges.toMutableSet()
        val currentNodeSet = graph.nodes.toMutableSet()

        graph.contractCircuitGraphs.forEach { contractCircuitGraph ->
            val newNode = WeightedGraph.Node(
                weight = 0,
                value = contractCircuitGraph.moduleInvocationNode as Node,
            )

            val oldIncomingEdges = currentEdgeSet.filter { it.sink == contractCircuitGraph.inputNode }
            val oldOutgoingEdges = currentEdgeSet.filter { it.source == contractCircuitGraph.outputNode }

            val newIncomingEdges = oldIncomingEdges.map { oldIncomingEdge ->
                WeightedGraph.Edge(
                    source = oldIncomingEdge.source,
                    sink = newNode,
                    weight = oldIncomingEdge.weight,
                    value = oldIncomingEdge.value,
                )
            }

            val newOutgoingEdges = oldOutgoingEdges.map { oldOutgoingEdge ->
                WeightedGraph.Edge(
                    source = newNode,
                    sink = oldOutgoingEdge.sink,
                    weight = oldOutgoingEdge.weight,
                    value = oldOutgoingEdge.value,
                )
            }

            // Remove the old graph
            currentNodeSet.remove(contractCircuitGraph.inputNode)
            currentNodeSet.remove(contractCircuitGraph.outputNode)
            currentEdgeSet.remove(contractCircuitGraph.edge)

            // Unhook from graph
            currentEdgeSet.removeAll(oldIncomingEdges.toSet())
            currentEdgeSet.removeAll(oldOutgoingEdges.toSet())

            // Add the new graph
            currentNodeSet.add(newNode)
            currentEdgeSet.addAll(newIncomingEdges)
            currentEdgeSet.addAll(newOutgoingEdges)
        }

        return LeisersonCircuitGraph(
            value = graph.value,
            nodes = currentNodeSet,
            edges = currentEdgeSet,
        )
    }

    private fun nodeType(node: Node) = if (node !is PredefinedFunctionNode) node::class.simpleName else node.predefinedFunction::class.simpleName

    private fun printGraph(graph: LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>) = buildString {
        println("PRINTING GRAPH:")
        println("  Nodes:")
        graph.nodes.forEach { node ->
            println("    ${node.weight}: ${node.value.name()} [${nodeType(node.value)}]")
        }
        println("  Edges:")
        graph.edges.forEach { edge ->
            println("    ${edge.weight}: ${edge.source.value.name()} -> ${edge.sink.value.name()}")
        }
    }


    fun retimeAll(propagationDelay: PropagationDelay, targetClockPeriod: Int): List<Module> {
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
        }
    }

}