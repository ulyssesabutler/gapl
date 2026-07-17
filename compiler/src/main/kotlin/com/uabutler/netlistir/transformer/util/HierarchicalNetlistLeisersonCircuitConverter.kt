package com.uabutler.netlistir.transformer.util

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.VirtualIONode
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter.WeightedNonRegisterConnectionGroup
import com.uabutler.netlistir.transformer.util.NodeCopier.copyBodyNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyInputNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyOutputNode
import com.uabutler.util.PropagationDelay
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph

object HierarchicalNetlistLeisersonCircuitConverter {

    fun fromModule(
        module: MutableModule,
        delay: PropagationDelay,
        childGraphs: Map<Module.Invocation, HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>>,
    ): HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>> {
        val leafNodes = module.getNodes()
            .filter { !NetlistLeisersonCircuitConverter.isRegisterNode(it) && it !is ModuleInvocationNode }
            .associateWith { irNode ->
                HierarchicalLeisersonCircuitGraph.LeafNode(
                    value = irNode,
                    weight = NetlistLeisersonCircuitConverter.getDelay(irNode, delay),
                )
            }

        val childNodes = module.getNodes()
            .filterIsInstance<ModuleInvocationNode>()
            .associateWith { irNode ->
                HierarchicalLeisersonCircuitGraph.ChildGraphNode(
                    value = irNode as Node,
                    childGraph = childGraphs[irNode.invocation]!!,
                )
            }

        val allNonVirtualNodes = buildMap<Node, HierarchicalLeisersonCircuitGraph.Node<Node>> {
            putAll(leafNodes)
            putAll(childNodes)
        }

        val superInputNode = HierarchicalLeisersonCircuitGraph.VirtualNode<Node>(
            value = VirtualIONode(identifier = "SuperInputNode", module) as Node
        )

        val superInputEdges: List<HierarchicalLeisersonCircuitGraph.Edge<Node, Collection<NonRegisterConnection>>> = module.getNodes()
            .filter { !NetlistLeisersonCircuitConverter.isRegisterNode(it) && it.inputWires().isEmpty() }
            .map { sourceNode ->
                val graphNode: HierarchicalLeisersonCircuitGraph.Node<Node> = when (sourceNode) {
                    is ModuleInvocationNode -> childNodes[sourceNode]!!
                    else -> leafNodes[sourceNode]!!
                }
                HierarchicalLeisersonCircuitGraph.Edge(
                    source = superInputNode,
                    sink = graphNode,
                    weight = 0,
                    value = emptyList<NonRegisterConnection>(),
                )
            }

        val superOutputNode = HierarchicalLeisersonCircuitGraph.VirtualNode<Node>(
            value = VirtualIONode(identifier = "SuperOutputNode", module) as Node
        )

        val superOutputEdges: List<HierarchicalLeisersonCircuitGraph.Edge<Node, Collection<NonRegisterConnection>>> = module.getOutputNodes().map { outputNode ->
            HierarchicalLeisersonCircuitGraph.Edge(
                source = leafNodes[outputNode]!!,
                sink = superOutputNode,
                weight = 0,
                value = emptyList<NonRegisterConnection>(),
            )
        }

        val edges = NetlistLeisersonCircuitConverter.getNonRegisterConnections(module)
            .map { (source, sink, weight) ->
                WeightedNonRegisterConnectionGroup(
                    sourceNode = source.parentWireVector.parentGroup.parentNode,
                    sinkNode = sink.parentWireVector.parentGroup.parentNode,
                    connections = listOf(NonRegisterConnection(source, sink)),
                    weight = weight,
                )
            }.let {
                NetlistLeisersonCircuitConverter.condenseWeightedNonRegisterConnectionGroups(it)
            }.map { group ->
                HierarchicalLeisersonCircuitGraph.Edge(
                    source = allNonVirtualNodes[group.sourceNode]!!,
                    sink = allNonVirtualNodes[group.sinkNode]!!,
                    weight = group.weight,
                    value = group.connections,
                )
            }

        return HierarchicalLeisersonCircuitGraph(
            value = module,
            nodes = leafNodes.values + childNodes.values + listOf(superInputNode, superOutputNode),
            edges = edges + superInputEdges + superOutputEdges,
        )
    }

    fun fromModules(
        modules: Collection<MutableModule>,
        delay: PropagationDelay,
    ): Collection<HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>> {
        val childGraphs = mutableMapOf<Module.Invocation, HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>>()
        val result = mutableListOf<HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>>()

        InvocationGraph(modules).topologicalSort().reversed().forEach { module ->
            val graph = fromModule(module, delay, childGraphs)
            childGraphs[module.invocation] = graph
            result.add(graph)
        }

        return result
    }

    fun toModule(
        graph: HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>,
    ): MutableModule {
        val oldModule = graph.value
        val newModule = MutableModule(oldModule.invocation)

        val inputWireMap = mutableMapOf<InputWire, InputWire>()
        val outputWireMap = mutableMapOf<OutputWire, OutputWire>()

        fun addNodeToMaps(newNode: NodeCopier.CreatedNode<*>) {
            newNode.wirePairs.input.forEach { (newWire, oldWire) -> inputWireMap[oldWire] = newWire }
            newNode.wirePairs.output.forEach { (newWire, oldWire) -> outputWireMap[oldWire] = newWire }
        }

        graph.nodes.forEach { graphNode ->
            when (graphNode) {
                is HierarchicalLeisersonCircuitGraph.LeafNode -> {
                    val irNode = graphNode.value
                    when (irNode) {
                        is InputNode -> addNodeToMaps(copyInputNode(irNode, newModule))
                        is OutputNode -> addNodeToMaps(copyOutputNode(irNode, newModule))
                        else -> addNodeToMaps(copyBodyNode(irNode as BodyNode, "FromHierarchical", newModule))
                    }
                }
                is HierarchicalLeisersonCircuitGraph.ChildGraphNode<*, *, *> -> {
                    addNodeToMaps(copyBodyNode(graphNode.value as ModuleInvocationNode, "FromHierarchical", newModule))
                }
                is HierarchicalLeisersonCircuitGraph.VirtualNode -> { /* skip */ }
            }
        }

        graph.edges
            .filter { it.source !is HierarchicalLeisersonCircuitGraph.VirtualNode && it.sink !is HierarchicalLeisersonCircuitGraph.VirtualNode }
            .map { edge ->
                WeightedNonRegisterConnectionGroup(
                    sourceNode = edge.source.value as Node,
                    sinkNode = edge.sink.value as Node,
                    connections = edge.value,
                    weight = edge.weight,
                )
            }.let { groups ->
                NetlistLeisersonCircuitConverter.condenseWeightedNonRegisterConnectionGroups(groups)
            }.forEach { group ->
                NetlistLeisersonCircuitConverter.addWeightedConnection(
                    module = newModule,
                    source = group.connections.map { outputWireMap[it.source]!! },
                    sink = group.connections.map { inputWireMap[it.sink]!! },
                    weight = group.weight,
                )
            }

        return newModule
    }

    fun toModules(
        graphs: Collection<HierarchicalLeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>>,
    ): Collection<MutableModule> = graphs.map { toModule(it) }

}
