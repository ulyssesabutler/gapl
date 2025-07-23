package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils

// data class IOWireVector(val identifier: String, val size: Int)
// data class IONode(val identifier: String, val wireVectors: List<IOWireVector>)

class Module(
    val identifier: String,
    inputNodesBuilder: (Module) -> Collection<InputNode>,
    outputNodesBuilder: (Module) -> Collection<OutputNode>,
    childNodesBuilder: (Module) -> Collection<BodyNode>,
) {

    data class Connection(
        val outputWire: OutputWire,
        val inputWire: InputWire,
    )

    private val inputNodes = inputNodesBuilder(this).associateBy { it.identifier }.toMutableMap()
    private val outputNodes = outputNodesBuilder(this).associateBy { it.identifier }.toMutableMap()
    private val bodyNodes = childNodesBuilder(this).associateBy { it.identifier }.toMutableMap()

    private val connections = mutableSetOf<Connection>()

    fun currentInputNodes() = inputNodes.toMap()
    fun currentOutputNodes() = outputNodes.toMap()
    fun currentBodyNodes() = bodyNodes.toMap()
    fun currentConnection() = connections.toSet()

    fun getInputNode(identifier: String) = inputNodes[identifier]
    fun getOutputNode(identifier: String) = outputNodes[identifier]
    fun getBodyNode(identifier: String) = bodyNodes[identifier]
    fun getConnectionsForOutputWire(outputWire: OutputWire) = connections.filter { it.outputWire == outputWire }.toSet()
    fun getConnectionForInputWireOrNull(inputWire: InputWire) = connections.firstOrNull { it.inputWire == inputWire }
    fun getConnectionForInputWire(inputWire: InputWire) = connections.firstOrNull { it.inputWire == inputWire } ?: throw Exception("Input wire $inputWire not connected")

    fun connect(inputWire: InputWire, outputWire: OutputWire) {
        // Validation: Each input wire should have exactly one source
        if (getConnectionForInputWireOrNull(inputWire) != null) {
            throw IllegalArgumentException("Input wire $inputWire already connected")
        }

        connections.add(Connection(outputWire, inputWire))
    }

    // The input wire uniquely identifies the connection
    fun disconnect(inputWire: InputWire) {
        connections.remove(getConnectionForInputWire(inputWire))
    }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "bodyNodes" to bodyNodes
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<Module>(
            self = this,
            other = other,
            { o -> identifier == o.identifier },
            { o -> bodyNodes.keys == o.bodyNodes.keys }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(identifier, bodyNodes.keys)
    }

}