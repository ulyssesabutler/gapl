package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils

data class IOWireVector(val identifier: String, val size: Int)
data class IONode(val identifier: String, val wireVectors: List<IOWireVector>)

class Module(
    val identifier: String,
    inputNodesBuilder: (Module) -> Collection<IONode>,
    outputNodesBuilder: (Module) -> Collection<IONode>,
    childNodesBuilder: (Module) -> Collection<Node>,
) {
    val nodes: Map<String, Node> = childNodesBuilder(this).associateBy { it.identifier }

    val inputNodes: Map<String, Node> = inputNodesBuilder(this).map { ioNode ->
        Node(
            identifier = ioNode.identifier,
            parentModule = this,
            inputWireVectorsBuilder = { parent ->
                ioNode.wireVectors.map {
                    InputWireVector(
                        identifier = it.identifier,
                        parentNode = parent,
                        size = it.size,
                    )
                }
            },
            outputWireVectorsBuilder = { emptySet() },
        )
    }.associateBy { it.identifier }

    val outputNodes: Map<String, Node> = outputNodesBuilder(this).map { ioNode ->
        Node(
            identifier = ioNode.identifier,
            parentModule = this,
            inputWireVectorsBuilder = { emptySet() },
            outputWireVectorsBuilder = { parent ->
                ioNode.wireVectors.map {
                    OutputWireVector(
                        identifier = it.identifier,
                        parentNode = parent,
                        size = it.size,
                    )
                }
            },
        )
    }.associateBy { it.identifier }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "nodes" to nodes
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<Module>(
            self = this,
            other = other,
            { o -> identifier == o.identifier },
            { o -> nodes.keys == o.nodes.keys }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(identifier, nodes.keys)
    }
}