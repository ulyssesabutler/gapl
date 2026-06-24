package com.uabutler.netlistir.netlist

import com.uabutler.verilogir.builder.creator.util.Identifier

class MutableModule(
    invocation: Invocation,
) : Module(invocation) {

    // Setters
    fun addInputNode(node: InputNode) { inputNodes[node.name()] = node }
    fun addOutputNode(node: OutputNode) { outputNodes[node.name()] = node }
    fun addBodyNode(node: BodyNode) { bodyNodes[node.name()] = node }

    fun removeNode(node: Node) {
        val nodeConnections = getConnectionsForNode(node)

        if (nodeConnections.isNotEmpty()) {
            throw Exception("Attempted to remove connected node. This is a bug in the compiler. ${nodeConnections.joinToString(", ")}")
        }

        when (node) {
            is InputNode -> inputNodes.remove(node.name())
            is OutputNode -> outputNodes.remove(node.name())
            is BodyNode -> bodyNodes.remove(node.name())
            is VirtualNode -> throw IllegalStateException("Virtual node should not be in module")
        }
    }

    fun connect(inputWire: InputWire, outputWire: OutputWire) {
        val testConnection = getConnectionForInputWireOrNull(inputWire)
        if (testConnection != null) {
            // TODO: This error message should also, ideally, show the connection statement that's causing the error.
            throw IllegalArgumentException("Input wire for ${Identifier.wire(inputWire.parentWireVector)} at ${inputWire.index} in ${inputWire.parentWireVector.parentGroup.parentNode.parentModule.invocation.gaplFunctionName} already connected")
        }

        val connection = Connection(outputWire, inputWire)

        connectionSet.add(connection)
        connectionByInput[inputWire] = connection
        connectionsByOutput[outputWire] = connectionsByOutput.getOrDefault(outputWire, emptyList()) + connection
    }

    fun disconnect(inputWire: InputWire) {
        val connection = getConnectionForInputWire(inputWire)
        connectionSet.remove(connection)
        connectionByInput.remove(inputWire)
        connectionsByOutput[connection.source] = connectionsByOutput[connection.source]!! - connection
    }


}
