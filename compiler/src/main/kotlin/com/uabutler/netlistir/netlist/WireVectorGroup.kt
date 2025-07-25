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
        val members: List<String>?,
        val indices: List<Int>?,
        val range: IntRange?,
    ) {
        val wireVectors: List<WireVector.Projection<*>> = sourceGroup.wireVectors.mapNotNull { it.projection(members, indices, range) }

        override fun toString(): String {
            return ObjectUtils.toStringBuilder(
                obj = this,
                normalProps = mapOf(
                    "wireVectors" to wireVectors,
                ),
                identityProps = mapOf(
                    "sourceGroup" to sourceGroup,
                )
            )
        }
    }

    fun projection(members: List<String>, indices: List<Int>, range: IntRange?): Projection<T> {
        return Projection(this, members, indices, range)
    }

    fun projection(): Projection<T> {
        return Projection(this, null, null, null)
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
}

class InputWireVectorGroup(
    identifier: String,
    parentNode: Node,
    structure: InterfaceStructure,
) : WireVectorGroup<InputWireVector>(identifier, parentNode, structure) {

    override val wireVectors: List<InputWireVector> = InterfaceFlattener.fromInterfaceStructure(structure).map {
        InputWireVector(
            identifier = it.identifier,
            dimensions = it.dimensions,
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
            dimensions = it.dimensions,
            parentGroup = this,
            size = it.width,
        )
    }

    override fun wires(): List<OutputWire> = wireVectors.flatMap { it.wires }
}
