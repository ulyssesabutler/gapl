package com.uabutler.netlistir.transformer.util

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
import com.uabutler.netlistir.transformer.util.NodeCopier.copyBodyNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NodeCopier.copyInputNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyOutputNode
import com.uabutler.netlistir.util.RegisterFunction
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

    private fun isRegisterNode(node: Node): Boolean {
        return node is PredefinedFunctionNode && node.predefinedFunction is RegisterFunction
    }

    private fun getNonRegisterConnections(module: MutableModule): Collection<WeightedNonRegisterConnection> {
        val registerWires = module.getNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction is RegisterFunction }
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

    private fun condenseWeightedNonRegisterConnectionGroups(connectionGroups: Collection<WeightedNonRegisterConnectionGroup>): Collection<WeightedNonRegisterConnectionGroup> {
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

    private fun nodeType(node: Node) = if (node !is PredefinedFunctionNode) node::class.simpleName else node.predefinedFunction::class.simpleName

    private fun printGraph(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>) = buildString {
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

    private fun addWeightedConnection(module: MutableModule, source: List<OutputWire>, sink: List<InputWire>, weight: Int) {
        val sourceWires = if (weight > 0) {
            val registerFunction = RegisterFunction(
                storageStructure = VectorInterfaceStructure(
                    vectoredInterface = WireInterfaceStructure,
                    size = source.size,
                )
            )

            val registerNode = PredefinedFunctionNode(
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

            module.addBodyNode(registerNode)

            addWeightedConnection(module, source, registerNode.inputWires(), weight - 1)

            registerNode.outputWires()
        } else {
            source
        }

        sink.zip(sourceWires).forEach { (sinkWire, sourceWire) ->
            module.connect(sinkWire, sourceWire)
        }
    }

    private fun addWeightedConnection(module: MutableModule, weightedConnection: WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>) {
        addWeightedConnection(
            module = module,
            source = weightedConnection.value.map { it.source },
            sink = weightedConnection.value.map { it.sink },
            weight = weightedConnection.weight,
        )
    }

    fun toModule(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>): MutableModule {
        val oldModule = graph.value

        // Validation
        val edgesAttachedToNode = graph.edges.groupBy { it.source } + graph.edges.groupBy { it.sink }

        graph.nodes.forEach { node ->
            if (node.value is VirtualIONode) {
                edgesAttachedToNode[node]?.forEach { edge ->
                    if (edge.weight != 0) throw Exception("Virtual IO node cannot have non-zero weight")
                }
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

        condensedGraph.edges.asSequence()
            .forEach { addWeightedConnection(newModule, it) }

        return newModule
    }

}