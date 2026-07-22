package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.ParameterValue
import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.netlistir.util.NodeCopier

open class Module(
    val invocation: Invocation,
) {

    data class Invocation(
        val gaplFunctionName: String,
        val interfaces: List<InterfaceStructure>,
        val parameters: List<ParameterValue<*>>,
    )

    data class Connection(
        val source: OutputWire,
        val sink: InputWire,
    ) {
        override fun toString() = buildString {
            fun component(wire: Wire, index: Int) = buildString {
                append(Identifier.wire(wire.parentWireVector).padEnd(25, ' '))
                append("[")
                append(index.toString().padStart(3, ' '))
                append("]")
            }

            append(component(source, source.index))
            append(" -> ")
            append(component(sink, sink.index))
        }
    }

    fun identifier() = Identifier.module(invocation)

    // Nodes and Connections
    protected val inputNodes = mutableMapOf<String, InputNode>()
    protected val outputNodes = mutableMapOf<String, OutputNode>()
    protected val bodyNodes = mutableMapOf<String, BodyNode>()

    protected val connectionSet = mutableSetOf<Connection>()
    protected val connectionByInput = mutableMapOf<InputWire, Connection>()
    protected val connectionsByOutput = mutableMapOf<OutputWire, List<Connection>>()

    // Single Getters
    fun getInputNode(identifier: String) = inputNodes[identifier]!!
    fun getOutputNode(identifier: String) = outputNodes[identifier]!!
    fun getBodyNode(identifier: String) = bodyNodes[identifier]!!

    fun getConnectionForInputWireOrNull(inputWire: InputWire) = connectionByInput[inputWire]
    fun getConnectionForInputWire(inputWire: InputWire) = getConnectionForInputWireOrNull(inputWire) ?: throw Exception("Input wire $inputWire of ${Identifier.wire(inputWire.parentWireVector)} at ${inputWire.index} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName}:${inputWire.parentWireVector.parentGroup.parentNode.nodeType()} not connected")

    // Multi Getters
    fun getInputNodes() = inputNodes.values.toList()
    fun getOutputNodes() = outputNodes.values.toList()
    fun getBodyNodes() = bodyNodes.values.toList()
    fun getNodes() = getInputNodes() + getOutputNodes() + getBodyNodes()

    fun getConnections() = connectionSet.toSet()
    fun getConnectionsForOutputWire(outputWire: OutputWire) = connectionsByOutput[outputWire]?.toSet() ?: emptySet()
    fun getConnectionsForNodeInput(node: Node) = node.inputWires().mapNotNull { getConnectionForInputWireOrNull(it) }
    fun getConnectionsForNodeOutput(node: Node) = node.outputWires().flatMap { getConnectionsForOutputWire(it) }
    fun getConnectionsForNode(node: Node) = getConnectionsForNodeInput(node) + getConnectionsForNodeOutput(node)

    fun validateModule() {
        getNodes()
            .flatMap { it.inputWires() }
            .forEach { wire -> getConnectionForInputWire(wire) }
    }

    fun toMutableModule(): MutableModule {
        val newModule = MutableModule(invocation)

        var wirePairs = NodeCopier.WirePairs(emptyList(), emptyList())

        for (node in getInputNodes()) {
            wirePairs += NodeCopier.copyInputNode(node, newModule).wirePairs
        }

        for (node in getOutputNodes()) {
            wirePairs += NodeCopier.copyOutputNode(node, newModule).wirePairs
        }

        for (node in getBodyNodes()) {
            wirePairs += NodeCopier.copyBodyNode(node, invocation.gaplFunctionName, newModule, { _, n -> n }).wirePairs
        }

        val newToOldInput = wirePairs.input.associate { it.current to it.inlining }
        val oldToNewOutput = wirePairs.output.associate { it.inlining to it.current }

        (newModule.getOutputNodes() + newModule.getBodyNodes()).forEach { node ->
            node.inputWires().forEach { inputWire ->
                val oldInputWire = newToOldInput[inputWire]!!
                val oldConnection = getConnectionForInputWireOrNull(oldInputWire) ?: return@forEach
                val newSourceWire = oldToNewOutput[oldConnection.source]!!
                newModule.connect(inputWire, newSourceWire)
            }
        }

        newModule.validateModule()

        return newModule
    }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "invocation" to invocation,
                "inputNodes" to inputNodes,
                "outputNodes" to outputNodes,
                "bodyNodes" to bodyNodes,
                "connections" to connectionSet,
            )
        )
    }
}
