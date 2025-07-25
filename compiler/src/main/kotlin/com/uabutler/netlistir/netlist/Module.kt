package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.ParameterValue
import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.util.Named
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
        val outputWire: OutputWire,
        val inputWire: InputWire,
    )

    // Nodes and Connections
    private val inputNodes = mutableListOf<InputNode>()
    private val outputNodes = mutableListOf<OutputNode>()
    private val bodyNodes = mutableListOf<BodyNode>()

    private val connections = mutableSetOf<Connection>()

    // Single Getters
    fun getInputNode(identifier: String) = inputNodes.first { it.identifier == identifier }
    fun getOutputNode(identifier: String) = outputNodes.first { it.identifier == identifier }
    fun getBodyNode(identifier: String) = bodyNodes.first { it.identifier == identifier }

    fun getConnectionForInputWireOrNull(inputWire: InputWire) = connections.firstOrNull { it.inputWire == inputWire }
    fun getConnectionForInputWire(inputWire: InputWire) = connections.firstOrNull { it.inputWire == inputWire } ?: throw Exception("Input wire $inputWire of ${Identifier.wire(inputWire.parentWireVector)} at ${inputWire.index} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName} not connected")

    // Multi Getters
    fun getInputNodes() = inputNodes.toList()
    fun getOutputNodes() = outputNodes.toList()
    fun getBodyNodes() = bodyNodes.toList()
    fun getNodes() = getInputNodes() + getOutputNodes() + getBodyNodes()

    fun getConnections() = connections.toSet()
    fun getConnectionsForOutputWire(outputWire: OutputWire) = connections.filter { it.outputWire == outputWire }.toSet()
    fun getConnectionsForNodeInput(node: Node) = node.inputWires().mapNotNull { getConnectionForInputWireOrNull(it) }
    fun getConnectionsForNodeOutput(node: Node) = node.outputWires().flatMap { getConnectionsForOutputWire(it) }
    fun getConnectionsForNode(node: Node) = getConnectionsForNodeInput(node) + getConnectionsForNodeOutput(node)

    // Setters
    fun addInputNode(node: InputNode) { inputNodes += node }
    fun addOutputNode(node: OutputNode) { outputNodes += node }
    fun addBodyNode(node: BodyNode) { bodyNodes += node }

    fun removeNode(node: Node) {
        // Validation: The node must be disconnected
        val connections = getConnectionsForNode(node)

        if (connections.isNotEmpty()) {
            throw Exception("Attempted to remove connected node. This is a bug in the compiler. ${connections.joinToString(", ")}")
        }

        when (node) {
            is InputNode -> inputNodes.remove(node)
            is OutputNode -> outputNodes.remove(node)
            is BodyNode -> bodyNodes.remove(node)
        }
    }

    fun connect(inputWire: InputWire, outputWire: OutputWire) {
        // Validation: Each input wire should have exactly one source
        val testConnection = getConnectionForInputWireOrNull(inputWire)
        if (testConnection != null) {
            throw IllegalArgumentException("Input wire $inputWire at ${inputWire.index} for ${Identifier.wire(inputWire.parentWireVector)} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName} already connected")
        }

        connections.add(Connection(outputWire, inputWire))
    }

    fun disconnect(inputWire: InputWire) {
        connections.remove(getConnectionForInputWire(inputWire))
    }

    //
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