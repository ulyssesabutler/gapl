package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.ParameterValue
import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.verilogir.builder.creator.util.Identifier

class Module(
    val invocation: Invocation,
) {

    /* This is a data class that stores all of the data needed to instantiate any module. That is, it contains enough
     * information to find the definition (using the identifier), and it contains the values for each parameter.
     *
     * Each unique set of module instantiation data will correspond to one verilog module. That said, depending on how
     * much resource sharing is possible, it might correspond to multiple verilog instantiations.
     *
     * We might need to modify this if we want to support something like function overloading in the future.
     */
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
    private val inputNodes = mutableMapOf<String, InputNode>()
    private val outputNodes = mutableMapOf<String, OutputNode>()
    private val bodyNodes = mutableMapOf<String, BodyNode>()

    private val connections = mutableSetOf<Connection>()
    private val connectionByInput = mutableMapOf<InputWire, Connection>()
    private val connectionsByOutput = mutableMapOf<OutputWire, List<Connection>>()

    // Single Getters
    fun getInputNode(identifier: String) = inputNodes[identifier]!!
    fun getOutputNode(identifier: String) = outputNodes[identifier]!!
    fun getBodyNode(identifier: String) = bodyNodes[identifier]!!

    fun getConnectionForInputWireOrNull(inputWire: InputWire) = connectionByInput[inputWire]
    fun getConnectionForInputWire(inputWire: InputWire) = getConnectionForInputWireOrNull(inputWire) ?: throw Exception("Input wire $inputWire of ${Identifier.wire(inputWire.parentWireVector)} at ${inputWire.index} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName} not connected")

    // Multi Getters
    fun getInputNodes() = inputNodes.values.toList()
    fun getOutputNodes() = outputNodes.values.toList()
    fun getBodyNodes() = bodyNodes.values.toList()
    fun getNodes() = getInputNodes() + getOutputNodes() + getBodyNodes()

    fun getConnections() = connections.toSet()
    fun getConnectionsForOutputWire(outputWire: OutputWire) = connectionsByOutput[outputWire]?.toSet() ?: emptySet()
    fun getConnectionsForNodeInput(node: Node) = node.inputWires().mapNotNull { getConnectionForInputWireOrNull(it) }
    fun getConnectionsForNodeOutput(node: Node) = node.outputWires().flatMap { getConnectionsForOutputWire(it) }
    fun getConnectionsForNode(node: Node) = getConnectionsForNodeInput(node) + getConnectionsForNodeOutput(node)

    // Setters
    fun addInputNode(node: InputNode) { inputNodes[node.name()] = node }
    fun addOutputNode(node: OutputNode) { outputNodes[node.name()] = node }
    fun addBodyNode(node: BodyNode) { bodyNodes[node.name()] = node }

    fun removeNode(node: Node) {
        // Validation: The node must be disconnected
        val connections = getConnectionsForNode(node)

        if (connections.isNotEmpty()) {
            throw Exception("Attempted to remove connected node. This is a bug in the compiler. ${connections.joinToString(", ")}")
        }

        when (node) {
            is InputNode -> inputNodes.remove(node.name())
            is OutputNode -> outputNodes.remove(node.name())
            is BodyNode -> bodyNodes.remove(node.name())
        }
    }

    fun connect(inputWire: InputWire, outputWire: OutputWire) {
        // Validation: Each input wire should have exactly one source
        val testConnection = getConnectionForInputWireOrNull(inputWire)
        if (testConnection != null) {
            // TODO: This error message should also, ideally, show the connection statement that's causing the error.
            throw IllegalArgumentException("Input wire for ${Identifier.wire(inputWire.parentWireVector)} at ${inputWire.index} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName} already connected")
        }

        val connection = Connection(outputWire, inputWire)

        connections.add(connection)
        connectionByInput[inputWire] = connection
        connectionsByOutput[outputWire] = connectionsByOutput.getOrDefault(outputWire, emptyList()) + connection
    }

    fun disconnect(inputWire: InputWire) {
        val connection = getConnectionForInputWire(inputWire)
        connections.remove(connection)
        connectionByInput.remove(inputWire)
        connectionsByOutput[connection.source] = connectionsByOutput[connection.source]!! - connection
    }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "invocation" to invocation,
                "inputNodes" to inputNodes,
                "outputNodes" to outputNodes,
                "bodyNodes" to bodyNodes,
                "connections" to connections,
            )
        )
    }
}