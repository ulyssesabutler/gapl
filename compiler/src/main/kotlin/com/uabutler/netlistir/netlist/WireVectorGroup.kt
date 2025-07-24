package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils

sealed class WireVectorGroup<T : WireVector<*>>(val identifier: String, val parentNode: Node) {
    abstract val wireVectors: List<T>

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "wireVectors" to wireVectors
            ),
            identityProps = mapOf(
                "parentNode" to parentNode
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<WireVectorGroup<*>>(
            self = this,
            other = other,
            { o -> identifier == o.identifier },
            { o -> ObjectUtils.identifierEquals(
                selfValue = parentNode,
                otherValue = o.parentNode,
                identifierAccessor = { it.identifier }
            ) },
            { o -> wireVectors == o.wireVectors }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            identifier,
            parentNode.identifier,
            wireVectors
        )
    }
}

class InputWireVectorGroup(
    identifier: String,
    parentNode: Node,
    wireVectorsBuilder: (InputWireVectorGroup) -> List<InputWireVector>
) : WireVectorGroup<InputWireVector>(identifier, parentNode) {
    override val wireVectors: List<InputWireVector> = wireVectorsBuilder(this)
}

class OutputWireVectorGroup(
    identifier: String,
    parentNode: Node,
    wireVectorsBuilder: (OutputWireVectorGroup) -> List<OutputWireVector>
) : WireVectorGroup<OutputWireVector>(identifier, parentNode) {
    override val wireVectors: List<OutputWireVector> = wireVectorsBuilder(this)
}
