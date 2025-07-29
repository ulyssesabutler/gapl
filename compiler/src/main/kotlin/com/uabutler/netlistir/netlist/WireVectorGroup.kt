package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.builder.util.InterfaceFlattener
import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.util.ObjectUtils
import kotlin.compareTo

sealed class WireVectorGroup<T : WireVector<*>>(
    val identifier: String,
    val parentNode: Node,
    val gaplStructure: InterfaceStructure,
) {
    abstract val wireVectors: List<T>

    class Projection<T : WireVector<*>>(
        val sourceGroup: WireVectorGroup<T>,
        val projectionIdentifier: List<String>?,
        val range: IntRange?,
    ) {
        val wireVectors: List<WireVector.Projection<*>> = projectionIdentifier?.let {
            sourceGroup.wireVectors
                .filter { wireVector ->
                    wireVector.identifier.size >= projectionIdentifier.size &&
                            wireVector.identifier.subList(0, projectionIdentifier.size) == projectionIdentifier
                }
                .map { wireVector ->
                    @Suppress("UNCHECKED_CAST")
                    (wireVector as WireVector<Wire>).projection(range)
                }
        } ?: sourceGroup.wireVectors.map { it.projection(range) }
    }

    fun projection(identifier: List<String>? = null, range: IntRange? = null): Projection<T> {
        return Projection(this, identifier, range)
    }

    open fun wires(): List<Wire> = wireVectors.flatMap { it.wires }

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
    structure: InterfaceStructure,
) : WireVectorGroup<InputWireVector>(identifier, parentNode, structure) {

    override val wireVectors: List<InputWireVector> = InterfaceFlattener.fromInterfaceStructure(structure).map {
        InputWireVector(
            identifier = it.identifier,
            parentGroup = this,
            size = it.width,
        )
    }

    override fun wires(): List<InputWire> = wireVectors.flatMap { it.wires }

}

class OutputWireVectorGroup(
    identifier: String,
    parentNode: Node,
    structure: InterfaceStructure,
) : WireVectorGroup<OutputWireVector>(identifier, parentNode, structure) {

    override val wireVectors = InterfaceFlattener.fromInterfaceStructure(structure).map {
        OutputWireVector(
            identifier = it.identifier,
            parentGroup = this,
            size = it.width,
        )
    }

    override fun wires(): List<OutputWire> = wireVectors.flatMap { it.wires }
}
