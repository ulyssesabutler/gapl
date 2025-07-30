package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.util.Range

sealed class WireVector<T : Wire>(
    val identifier: List<String>,
    val dimensions: List<Int>,
    val parentGroup: WireVectorGroup<*>
) {
    abstract val wires: List<T>

    class Projection<T : Wire>(
        val sourceWireVector: WireVector<T>,
        val range: IntRange? = null,
    ) {
        val wires: List<T> = range?.let { sourceWireVector.wires.filterIndexed { index, _ -> index in range } } ?: sourceWireVector.wires

        override fun toString(): String {
            return ObjectUtils.toStringBuilder(
                obj = this,
                normalProps = mapOf(
                    "range" to range,
                ),
                identityProps = mapOf(
                    "sourceWireVector" to sourceWireVector,
                )
            )
        }
    }

    fun projection(members: List<String>? = null, indices: List<Int>?, range: IntRange?): Projection<T>? {
        if (members != null && (identifier.size < members.size || identifier.subList(0, members.size) != members)) {
            return null
        }

        if (members == null || indices == null) {
            return Projection(this, 0 until wires.size)
        }

        return Projection(
            sourceWireVector = this,
            range = Range.from(
                dimensions = dimensions,
                indices = indices,
                range = range,
            )
        )
    }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "wires" to wires
            ),
            identityProps = mapOf(
                "parentGroup" to parentGroup
            )
        )
    }
}

class InputWireVector(
    identifier: List<String>,
    dimensions: List<Int>,
    parentGroup: InputWireVectorGroup,
    wiresBuilder: (InputWireVector) -> List<InputWire>
) : WireVector<InputWire>(identifier, dimensions, parentGroup) {
    constructor(
        identifier: List<String>,
        dimensions: List<Int>,
        parentGroup: InputWireVectorGroup,
        size: Int,
    ) : this(
        identifier = identifier,
        dimensions = dimensions,
        parentGroup = parentGroup,
        wiresBuilder = {
            List(size) { index ->
                InputWire(
                    index = index,
                    parentWireVector = it,
                )
            }
        }
    )

    override val wires: List<InputWire> = wiresBuilder(this)
}

class OutputWireVector(
    identifier: List<String>,
    dimensions: List<Int>,
    parentGroup: OutputWireVectorGroup,
    wiresBuilder: (OutputWireVector) -> List<OutputWire>
) : WireVector<OutputWire>(identifier, dimensions, parentGroup) {
    constructor(
        identifier: List<String>,
        dimensions: List<Int>,
        parentGroup: OutputWireVectorGroup,
        size: Int,
    ) : this(
        identifier = identifier,
        dimensions = dimensions,
        parentGroup = parentGroup,
        wiresBuilder = {
            List(size) { index ->
                OutputWire(
                    index = index,
                    parentWireVector = it,
                )
            }
        }
    )

    override val wires: List<OutputWire> = wiresBuilder(this)
}
