package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils

sealed class WireVector<T : Wire>(val identifier: String, val parentGroup: WireVectorGroup<*>) {
    abstract val wires: List<T>

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

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<WireVector<*>>(
            self = this,
            other = other,
            { o -> identifier == o.identifier },
            { o -> ObjectUtils.identifierEquals(
                selfValue = parentGroup,
                otherValue = o.parentGroup,
                identifierAccessor = { it.identifier }
            ) },
            { o -> wires == o.wires }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            identifier,
            parentGroup.identifier,
            wires
        )
    }
}

class InputWireVector(
    identifier: String,
    parentGroup: InputWireVectorGroup,
    wiresBuilder: (InputWireVector) -> List<InputWire>
) : WireVector<InputWire>(identifier, parentGroup) {
    constructor(
        identifier: String,
        parentGroup: InputWireVectorGroup,
        size: Int,
    ) : this(
        identifier = identifier,
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
    identifier: String,
    parentGroup: OutputWireVectorGroup,
    wiresBuilder: (OutputWireVector) -> List<OutputWire>
) : WireVector<OutputWire>(identifier, parentGroup) {
    constructor(
        identifier: String,
        parentGroup: OutputWireVectorGroup,
        size: Int,
    ) : this(
        identifier = identifier,
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
