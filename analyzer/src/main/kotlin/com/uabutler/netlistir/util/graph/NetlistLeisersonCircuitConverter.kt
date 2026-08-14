package com.uabutler.netlistir.util.graph

import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.IONode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.netlist.VirtualIONode
import com.uabutler.netlistir.netlist.VirtualNode
import com.uabutler.netlistir.util.NodeCopier
import com.uabutler.netlistir.util.NodeCopier.copyBodyNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.util.NodeCopier.copyInputNode
import com.uabutler.netlistir.util.NodeCopier.copyOutputNode
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.netlistir.util.isRegister
import com.uabutler.util.AnonymousIdentifierGenerator
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import kotlin.sequences.forEach

object NetlistLeisersonCircuitConverter {

    data class NonRegisterConnection(
        val source: OutputWire,
        val sink: InputWire,
    )

    data class WeightedNonRegisterConnection(
        val source: OutputWire,
        val sink: InputWire,
        val weight: Int,
    )

    data class WeightedNonRegisterConnectionGroup(
        val sourceNode: Node,
        val sinkNode: Node,
        val weight: Int,
        val connections: Collection<NonRegisterConnection>,
    )

    internal fun isRegisterNode(node: Node): Boolean {
        return node is PredefinedFunctionNode && node.predefinedFunction.isRegister
    }

    internal fun getNonRegisterConnections(module: MutableModule): Collection<WeightedNonRegisterConnection> {
        val registerWires = module.getNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction.isRegister }
            .flatMap { it.outputWires().zip(it.inputWires()) }
            .associate { it }

        fun getNonRegisterSourceWithWeight(originalSink: InputWire, currentSink: InputWire = originalSink, currentWeight: Int = 0): WeightedNonRegisterConnection {

            module.getConnectionForInputWire(currentSink).source.let { source ->
                val node = source.parentWireVector.parentGroup.parentNode
                return if (isRegisterNode(node)) {
                    getNonRegisterSourceWithWeight(originalSink, registerWires[source]!!, currentWeight + 1)
                } else {
                    WeightedNonRegisterConnection(source, originalSink, currentWeight)
                }
            }
        }

        return module.getNodes()
            .filter { !isRegisterNode(it) }
            .flatMap { it.inputWires() }
            .map { getNonRegisterSourceWithWeight(it) }
    }

    internal fun condenseWeightedNonRegisterConnectionGroups(connectionGroups: Collection<WeightedNonRegisterConnectionGroup>): Collection<WeightedNonRegisterConnectionGroup> {
         return connectionGroups.groupBy { it.sourceNode }.flatMap { (sourceNode, sourceGroup) ->
            sourceGroup.groupBy { it.sinkNode }.flatMap { (sinkNode, sourceSinkGroup) ->
                sourceSinkGroup.groupBy { it.weight }.map { (weight, weightGroup) ->
                    WeightedNonRegisterConnectionGroup(
                        sourceNode = sourceNode,
                        sinkNode = sinkNode,
                        weight = weight,
                        connections = weightGroup.flatMap { it.connections },
                    )
                }
            }
        }
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

    fun getDelay(node: Node, delay: PropagationDelay): Int {
        return when (node) {
            is VirtualNode,
            is IONode,
            is ModuleInvocationNode,
            is PassThroughNode
                -> 0
            else
                -> delay.forNode(node)
        }
    }

    fun fromModule(module: MutableModule, delay: PropagationDelay, maintainTiming: Boolean): LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>> {
        Logger.start("Converting from module to Leiserson circuit graph")
        val nodes = module.getNodes()
            .filter { !isRegisterNode(it) }
            .associateWith { moduleNode ->
                WeightedGraph.Node(
                    weight = getDelay(moduleNode, delay),
                    value = moduleNode
                )
            }

        val superInputNode = WeightedGraph.Node<Node>(weight = 0, value = VirtualIONode(identifier = "SuperInputNode", module))
        val superInputEdges: List<WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>> = module.getNodes()
            .filter { it.inputWires().isEmpty() }
            .map { sourceNode ->
                WeightedGraph.Edge(
                    source = superInputNode,
                    sink = nodes[sourceNode]!!,
                    weight = 0,
                    value = emptyList(),
                )
            }


        val superOutputNode = WeightedGraph.Node<Node>(weight = 0, value = VirtualIONode(identifier = "SuperOutputNode", module))
        val superOutputEdges: List<WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>> = module.getOutputNodes()
            .map { outputNode ->
                WeightedGraph.Edge(
                    source = nodes[outputNode]!!,
                    sink = superOutputNode,
                    weight = 0,
                    value = emptyList(),
                )
            }

        val edges = getNonRegisterConnections(module)
            .map { (source, sink, weight) ->
                WeightedNonRegisterConnectionGroup(
                    sourceNode = source.parentWireVector.parentGroup.parentNode,
                    sinkNode = sink.parentWireVector.parentGroup.parentNode,
                    connections = listOf(NonRegisterConnection(source, sink)),
                    weight = weight,
                )
            }.let {
                condenseWeightedNonRegisterConnectionGroups(it)
            }.map {
                WeightedGraph.Edge(
                    source = nodes[it.sourceNode]!!,
                    sink = nodes[it.sinkNode]!!,
                    weight = it.weight,
                    value = it.connections,
                )
            }

        val loopEdge: WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>? = if (maintainTiming) {
            WeightedGraph.Edge(
                source = superOutputNode,
                sink = superInputNode,
                weight = 0,
                value = emptyList(),
            )
        } else {
            null
        }

        Logger.trace { "Node Count:              ${nodes.size}" }
        Logger.trace { "Edge Count:              ${edges.size}" }
        Logger.trace { "Super Input Edge Count:  ${superInputEdges.size}" }
        Logger.trace { "Super Output Edge Count: ${superInputEdges.size}" }
        Logger.trace { "Loop Edge Count:         ${loopEdge?.let { 1 } ?: 0}" }

        return LeisersonCircuitGraph(
            value = module,
            nodes = nodes.values + listOf(superInputNode, superOutputNode),
            edges = edges + superInputEdges + superOutputEdges + listOfNotNull(loopEdge),
        ).also { Logger.finish() }
    }

    /** One retimed connection: [sink] is driven by [source] delayed by [weight] clock cycles. */
    data class WeightedWireConnection(
        val source: OutputWire,
        val sink: InputWire,
        val weight: Int,
    )

    private fun createRegisterNode(module: MutableModule, width: Int): PredefinedFunctionNode {
        val registerFunction = RegisterFunction(
            storageStructure = VectorInterfaceStructure(
                vectoredInterface = WireInterfaceStructure,
                size = width,
            )
        )

        return PredefinedFunctionNode(
            identifier = AnonymousIdentifierGenerator.genIdentifier(), // TODO: Use a better identifier
            parentModule = module,
            inputWireVectorGroupsBuilder = { node ->
                registerFunction.inputs.map { it.toInputWireVectorGroup(node) }
            },
            outputWireVectorGroupsBuilder = { node ->
                registerFunction.outputs.map { it.toOutputWireVectorGroup(node) }
            },
            predefinedFunction = registerFunction,
        )
    }

    /**
     * Materialises every retimed connection in a module, sharing registers across fanout.
     *
     * Each driving wire gets **one** shift register, as deep as its most-delayed consumer needs, and
     * every consumer of that wire taps it at its own depth. So a bit driving three sinks two cycles
     * downstream costs two flip-flops, not six, and a bit driving one sink at depth 2 and another at
     * depth 5 costs five, not seven.
     *
     * This has to be done for the whole module at once rather than per edge: two edges out of the
     * same node are exactly the case that shares, and an edge-at-a-time API cannot see the sharing
     * opportunity. [com.uabutler.netlistir.transformer.util.retiming] relies on the cost model here
     * matching what the register-minimisation objective charges for - see
     * `MinimalRegisterSolver`'s objective, which prices a retiming as the sum over driving bits of
     * that bit's maximum consumer depth.
     *
     * Wires of one node are collected into a single register per stage, ordered by the node's own
     * output-wire order so the emitted netlist is deterministic. Every map here is a
     * [LinkedHashMap]: netlist [com.uabutler.netlistir.netlist.Wire]/[Node] have no `equals`
     * override, so it is already identity-keyed, and unlike an `IdentityHashMap` it also iterates in
     * a stable order.
     */
    internal fun addSharedWeightedConnections(module: MutableModule, connections: Collection<WeightedWireConnection>) {
        val byDrivingNode = LinkedHashMap<Node, MutableList<WeightedWireConnection>>()
        connections.forEach { connection ->
            require(connection.weight >= 0) {
                "Cannot materialise a connection with negative register count: " +
                    "${connection.source.parentWireVector.parentGroup.parentNode.name()} -> " +
                    "${connection.sink.parentWireVector.parentGroup.parentNode.name()} has weight ${connection.weight}"
            }
            val drivingNode = connection.source.parentWireVector.parentGroup.parentNode
            byDrivingNode.getOrPut(drivingNode) { mutableListOf() }.add(connection)
        }

        byDrivingNode.forEach { (drivingNode, nodeConnections) ->
            // How deep each driven bit's shift register has to be: its most-delayed consumer.
            val requiredDepth = LinkedHashMap<OutputWire, Int>()
            nodeConnections.forEach { connection ->
                val existing = requiredDepth[connection.source] ?: 0
                requiredDepth[connection.source] = maxOf(existing, connection.weight)
            }

            // Order the register's bits by the node's own output order rather than by whichever
            // consumer happened to be visited first, so the emitted Verilog is stable.
            val wireOrder = LinkedHashMap<OutputWire, Int>()
            drivingNode.outputWires().forEachIndexed { index, wire -> wireOrder[wire] = index }
            val orderedWires = requiredDepth.keys.sortedBy { wireOrder[it] ?: Int.MAX_VALUE }

            // wireAtDepth[d][w] is the wire carrying w's value after d registers. Stage d only
            // carries the bits that still have a consumer at depth d or deeper, so the chain narrows
            // as shallower consumers drop off.
            val currentWire = LinkedHashMap<OutputWire, OutputWire>()
            orderedWires.forEach { currentWire[it] = it }
            val wireAtDepth = mutableListOf<Map<OutputWire, OutputWire>>(LinkedHashMap(currentWire))

            val maxDepth = requiredDepth.values.maxOrNull() ?: 0
            for (depth in 1..maxDepth) {
                val stageWires = orderedWires.filter { requiredDepth.getValue(it) >= depth }
                val registerNode = createRegisterNode(module, stageWires.size)
                module.addBodyNode(registerNode)

                registerNode.inputWires().forEachIndexed { index, inputWire ->
                    module.connect(inputWire, currentWire.getValue(stageWires[index]))
                }
                registerNode.outputWires().forEachIndexed { index, outputWire ->
                    currentWire[stageWires[index]] = outputWire
                }

                wireAtDepth.add(LinkedHashMap(currentWire))
            }

            nodeConnections.forEach { connection ->
                module.connect(connection.sink, wireAtDepth[connection.weight].getValue(connection.source))
            }
        }
    }

    internal fun weightedWireConnections(
        edge: WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>,
    ): List<WeightedWireConnection> = edge.value.map {
        WeightedWireConnection(source = it.source, sink = it.sink, weight = edge.weight)
    }

    fun toModule(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>): MutableModule {
        val oldModule = graph.value

        // Validation
        val edgesAttachedToNode = graph.edges.groupBy { it.source } + graph.edges.groupBy { it.sink }

        graph.nodes.forEach { node ->
            edgesAttachedToNode[node]?.forEach { edge ->
                if (node.value is VirtualIONode && edge.weight != 0) throw Exception("Virtual IO node ${node.value.name()} cannot have non-zero weight: ${edge.weight}")
                else if (edge.weight < 0) throw Exception("Negative weight edge for node ${node.value.name()}: ${edge.weight}")
            }
        }

        // First, create the new module
        val newModule = MutableModule(oldModule.invocation)

        // Next, create copies of the input, output, and non-register body nodes.
        // Maps of old to new
        val nodeMap = mutableMapOf<Node, Node>()
        val inputWireMap = mutableMapOf<InputWire, InputWire>()
        val outputWireMap = mutableMapOf<OutputWire, OutputWire>()

        fun addNodeToMaps(oldNode: Node, newNode: NodeCopier.CreatedNode<*>) {
            nodeMap[oldNode] = newNode.node
            newNode.wirePairs.input.forEach { (newWire, oldWire) -> inputWireMap[oldWire] = newWire }
            newNode.wirePairs.output.forEach { (newWire, oldWire) -> outputWireMap[oldWire] = newWire }
        }

        oldModule.getInputNodes().forEach { oldNode ->
            val copiedNode = copyInputNode(oldNode, newModule)
            addNodeToMaps(oldNode, copiedNode)
        }
        oldModule.getOutputNodes().forEach { oldNode ->
            val copiedNode = copyOutputNode(oldNode, newModule)
            addNodeToMaps(oldNode, copiedNode)
        }
        oldModule.getBodyNodes().filter { it !is PredefinedFunctionNode || it.predefinedFunction !is RegisterFunction }.forEach { oldNode ->
            val copiedNode = copyBodyNode(oldNode, "FromLeiserson", newModule)
            addNodeToMaps(oldNode, copiedNode)
        }

        val oldNetlistNodeToNewGraphNode = graph.nodes
            .filter { it.value !is VirtualNode }
            .associateWith {
                WeightedGraph.Node(
                    weight = it.weight,
                    value = nodeMap[it.value]!!,
                )
            }.mapKeys { it.key.value }

        val newGraphEdges = graph.edges
            .filter { it.source.value !is VirtualNode && it.sink.value !is VirtualNode }
            .map { edge ->
                WeightedNonRegisterConnectionGroup(
                    sourceNode = edge.source.value,
                    sinkNode = edge.sink.value,
                    connections = edge.value,
                    weight = edge.weight,
                )
            }.let {
                condenseWeightedNonRegisterConnectionGroups(it)
            }.map { group ->
                WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>(
                    source = oldNetlistNodeToNewGraphNode[group.sourceNode]!!,
                    sink = oldNetlistNodeToNewGraphNode[group.sinkNode]!!,
                    weight = group.weight,
                    value = group.connections.map {
                        NonRegisterConnection(
                            source = outputWireMap[it.source]!!,
                            sink = inputWireMap[it.sink]!!,
                        )
                    },
                )
            }

        val newGraphNodes = oldNetlistNodeToNewGraphNode.values

        val condensedGraph = LeisersonCircuitGraph(
            value = newModule,
            nodes = newGraphNodes,
            edges = newGraphEdges,
        )

        addSharedWeightedConnections(newModule, condensedGraph.edges.flatMap { weightedWireConnections(it) })

        return newModule
    }

}