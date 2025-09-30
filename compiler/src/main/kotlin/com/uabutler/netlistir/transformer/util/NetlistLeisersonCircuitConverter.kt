package com.uabutler.netlistir.transformer.util

import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyBodyNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NodeCopier.copyInputNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyOutputNode
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.AnonymousIdentifierGenerator
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

    private fun getNonRegisterConnections(module: Module): Collection<WeightedNonRegisterConnection> {
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

    fun printGraph(graph: LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>) = buildString {
        println("PRINTING GRAPH:")
        println("  Nodes:")
        graph.nodes.forEach { node ->
            println("    ${node.weight}: ${node.value.name()} [${node.value.typeString()}]")
        }
        println("  Edges:")
        graph.edges.forEach { edge ->
            println("    ${edge.weight}: ${edge.source.value.name()} -> ${edge.sink.value.name()}")
        }
    }

    fun fromModule(module: Module, delay: PropagationDelay): LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>> {
        val nodes = module.getNodes()
            .filter { !isRegisterNode(it) }
            .associateWith { moduleNode ->
                WeightedGraph.Node(
                    weight = delay.forNode(moduleNode),
                    value = moduleNode
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

        return LeisersonCircuitGraph(
            value = module,
            nodes = nodes.values,
            edges = edges,
        )
    }

    private fun addWeightedConnection(module: Module, source: List<OutputWire>, sink: List<InputWire>, weight: Int) {
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

    private fun addWeightedConnection(module: Module, weightedConnection: WeightedGraph.Edge<Node, Collection<NonRegisterConnection>>) {
        addWeightedConnection(
            module = module,
            source = weightedConnection.value.map { it.source },
            sink = weightedConnection.value.map { it.sink },
            weight = weightedConnection.weight,
        )
    }

    fun toModule(graph: LeisersonCircuitGraph<Module, Node, Collection<NonRegisterConnection>>): Module {
        val oldModule = graph.value

        // First, create the new module
        val newModule = Module(oldModule.invocation)

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

        val oldNetlistNodeToNewGraphNode = graph.nodes.associateWith {
            WeightedGraph.Node(
                weight = it.weight,
                value = nodeMap[it.value]!!,
            )
        }.mapKeys { it.key.value }

        val newGraphEdges = graph.edges.map { edge ->
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