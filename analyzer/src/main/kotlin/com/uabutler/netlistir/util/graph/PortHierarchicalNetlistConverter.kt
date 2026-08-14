package com.uabutler.netlistir.util.graph

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.netlistir.util.NodeCopier
import com.uabutler.netlistir.util.NodeCopier.copyBodyNode
import com.uabutler.netlistir.util.NodeCopier.copyInputNode
import com.uabutler.netlistir.util.NodeCopier.copyOutputNode
import com.uabutler.netlistir.util.graph.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.util.PropagationDelay
import com.uabutler.util.graph.PortHierarchicalCircuitGraph
import java.util.IdentityHashMap

/**
 * Netlist <-> [PortHierarchicalCircuitGraph] conversion, the per-port counterpart of
 * [HierarchicalNetlistLeisersonCircuitConverter].
 *
 * Two differences from that converter, both deliberate:
 *
 * - **No super-source/super-sink.** A module's boundary is its own [InputNode]/[OutputNode]s. That
 *   also means nothing pins literals and other source-less nodes to the module's input lag, which
 *   the super-source construction does as a side effect.
 * - **A call site becomes one node per port**, not one node. Which port a parent-side wire belongs
 *   to is recovered by name: `NodeBuilder` builds a `ModuleInvocationNode`'s wire groups from the
 *   callee's parameter names, and `Module.inputNodes`/`outputNodes` are keyed by those same names.
 */
typealias PortGraph = PortHierarchicalCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>
private typealias PortGraphNode = PortHierarchicalCircuitGraph.Node<Node>
private typealias PortLeafNode = PortHierarchicalCircuitGraph.LeafNode<Node>
private typealias PortChildNode = PortHierarchicalCircuitGraph.ChildPortNode<MutableModule, Node, Collection<NonRegisterConnection>>

object PortHierarchicalNetlistConverter {

    fun fromModule(
        module: MutableModule,
        delay: PropagationDelay,
        childGraphs: Map<Module.Invocation, PortGraph>,
    ): PortGraph {
        // LinkedHashMap, not IdentityHashMap: netlist Node has no equals/hashCode override, so a
        // plain map is already identity-keyed - and unlike IdentityHashMap it iterates in insertion
        // order. That matters because the node list built from these maps becomes graph.nodes, whose
        // order determines CP-SAT's variable order and the anchor node. An IdentityHashMap here made
        // the solver's results depend on JVM identity hash codes, i.e. on unrelated earlier
        // allocations - which showed up as compilation output depending on --log-level.
        val leafNodes = LinkedHashMap<Node, PortLeafNode>().apply {
            module.getNodes()
                .filter { !NetlistLeisersonCircuitConverter.isRegisterNode(it) && it !is ModuleInvocationNode }
                .forEach { irNode ->
                    put(
                        irNode,
                        PortHierarchicalCircuitGraph.LeafNode(
                            value = irNode,
                            weight = NetlistLeisersonCircuitConverter.getDelay(irNode, delay),
                        )
                    )
                }
        }

        // One graph node per (call site, port). Keyed by the invocation node so both endpoints of a
        // parent edge can find the right one, and by port name within it.
        val childPortNodes = LinkedHashMap<Node, MutableMap<PortKey, PortChildNode>>()

        module.getNodes().filterIsInstance<ModuleInvocationNode>().forEach { invocationNode ->
            val childGraph = childGraphs[invocationNode.invocation]
                ?: throw IllegalStateException(
                    "Child graph for ${invocationNode.invocation.gaplFunctionName} was not built before its caller ${module.invocation.gaplFunctionName}"
                )

            val byPort = mutableMapOf<PortKey, PortChildNode>()

            invocationNode.inputWireVectorGroups.forEach { group ->
                byPort[PortKey(group.identifier, isInput = true)] = PortHierarchicalCircuitGraph.ChildPortNode(
                    value = invocationNode as Node,
                    childGraph = childGraph,
                    port = childGraph.portNodeByName(group.identifier, isInput = true),
                    isInput = true,
                )
            }

            invocationNode.outputWireVectorGroups.forEach { group ->
                byPort[PortKey(group.identifier, isInput = false)] = PortHierarchicalCircuitGraph.ChildPortNode(
                    value = invocationNode as Node,
                    childGraph = childGraph,
                    port = childGraph.portNodeByName(group.identifier, isInput = false),
                    isInput = false,
                )
            }

            childPortNodes[invocationNode] = byPort
        }

        fun graphNodeForSource(wire: OutputWire): PortGraphNode {
            val owner = wire.parentWireVector.parentGroup.parentNode
            leafNodes[owner]?.let { return it }
            val portName = wire.parentWireVector.parentGroup.identifier
            return childPortNodes.getValue(owner)[PortKey(portName, isInput = false)]
                ?: throw IllegalStateException("No output port '$portName' on ${owner.name()}")
        }

        fun graphNodeForSink(wire: InputWire): PortGraphNode {
            val owner = wire.parentWireVector.parentGroup.parentNode
            leafNodes[owner]?.let { return it }
            val portName = wire.parentWireVector.parentGroup.identifier
            return childPortNodes.getValue(owner)[PortKey(portName, isInput = true)]
                ?: throw IllegalStateException("No input port '$portName' on ${owner.name()}")
        }

        // Group by (source graph node, sink graph node, weight). Deliberately not
        // NetlistLeisersonCircuitConverter.condenseWeightedNonRegisterConnectionGroups, which groups
        // by netlist node and would collapse two ports of one instance back into a single edge.
        val edgeGroups = LinkedHashMap<EdgeKey, MutableList<NonRegisterConnection>>()
        val edgeKeyNodes = mutableMapOf<EdgeKey, Pair<PortGraphNode, PortGraphNode>>()

        NetlistLeisersonCircuitConverter.getNonRegisterConnections(module).forEach { weighted ->
            val sourceNode = graphNodeForSource(weighted.source)
            val sinkNode = graphNodeForSink(weighted.sink)
            val key = EdgeKey(IdentityKey(sourceNode), IdentityKey(sinkNode), weighted.weight)
            edgeGroups.getOrPut(key) { mutableListOf() }.add(NonRegisterConnection(weighted.source, weighted.sink))
            edgeKeyNodes[key] = sourceNode to sinkNode
        }

        val edges = edgeGroups.map { (key, connections) ->
            val (source, sink) = edgeKeyNodes.getValue(key)
            PortHierarchicalCircuitGraph.Edge<Node, Collection<NonRegisterConnection>>(
                source = source,
                sink = sink,
                value = connections.toList(),
                weight = key.weight,
            )
        }

        val allNodes: List<PortGraphNode> =
            leafNodes.values.toList() + childPortNodes.values.flatMap { it.values }

        return PortHierarchicalCircuitGraph(
            value = module,
            nodes = allNodes,
            edges = edges,
            inputPorts = module.getInputNodes().map { leafNodes.getValue(it) },
            outputPorts = module.getOutputNodes().map { leafNodes.getValue(it) },
        )
    }

    fun fromModules(
        modules: Collection<MutableModule>,
        delay: PropagationDelay,
    ): List<PortGraph> {
        val childGraphs = mutableMapOf<Module.Invocation, PortGraph>()
        val result = mutableListOf<PortGraph>()

        InvocationGraph(modules).topologicalSort().reversed().forEach { module ->
            val graph = fromModule(module, delay, childGraphs)
            childGraphs[module.invocation] = graph
            result.add(graph)
        }

        return result
    }

    fun toModule(graph: PortGraph): MutableModule {
        val oldModule = graph.value
        val newModule = MutableModule(oldModule.invocation)

        val inputWireMap = mutableMapOf<InputWire, InputWire>()
        val outputWireMap = mutableMapOf<OutputWire, OutputWire>()

        fun addNodeToMaps(newNode: NodeCopier.CreatedNode<*>) {
            newNode.wirePairs.input.forEach { (newWire, oldWire) -> inputWireMap[oldWire] = newWire }
            newNode.wirePairs.output.forEach { (newWire, oldWire) -> outputWireMap[oldWire] = newWire }
        }

        graph.nodes.filterIsInstance<PortLeafNode>().forEach { leafNode ->
            when (val irNode = leafNode.value) {
                is InputNode -> addNodeToMaps(copyInputNode(irNode, newModule))
                is OutputNode -> addNodeToMaps(copyOutputNode(irNode, newModule))
                else -> addNodeToMaps(copyBodyNode(irNode as BodyNode, "FromPortHierarchical", newModule))
            }
        }

        // One copy per call site, not one per port - the same ModuleInvocationNode now appears once
        // for every port it has.
        graph.childInstances().forEach { instance ->
            addNodeToMaps(copyBodyNode(instance.value as ModuleInvocationNode, "FromPortHierarchical", newModule))
        }

        NetlistLeisersonCircuitConverter.addSharedWeightedConnections(
            module = newModule,
            connections = graph.edges.flatMap { edge ->
                edge.value.map {
                    NetlistLeisersonCircuitConverter.WeightedWireConnection(
                        source = outputWireMap.getValue(it.source),
                        sink = inputWireMap.getValue(it.sink),
                        weight = edge.weight,
                    )
                }
            },
        )

        return newModule
    }

    fun toModules(graphs: Collection<PortGraph>): List<MutableModule> = graphs.map { toModule(it) }

    private data class PortKey(val name: String, val isInput: Boolean)

    /** Reference-equality wrapper, so structurally-equal graph nodes from distinct positions never merge. */
    private class IdentityKey(val node: PortGraphNode) {
        override fun equals(other: Any?) = other is IdentityKey && other.node === node
        override fun hashCode() = System.identityHashCode(node)
    }

    private data class EdgeKey(val source: IdentityKey, val sink: IdentityKey, val weight: Int)

    private fun PortGraph.portNodeByName(name: String, isInput: Boolean): PortLeafNode {
        val candidates = if (isInput) inputPorts else outputPorts
        return candidates.firstOrNull { it.value.name() == name }
            ?: throw IllegalStateException(
                "Module ${value.invocation.gaplFunctionName} has no ${if (isInput) "input" else "output"} port named '$name' " +
                    "(has ${candidates.map { it.value.name() }})"
            )
    }
}
