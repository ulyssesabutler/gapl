package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils

enum class WireType { INPUT, OUTPUT }

sealed class Wire(val index: Int) {
    abstract val parentWireVector: WireVector<out Wire>

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf("index" to index),
            identityProps = mapOf("parentWireVector" to parentWireVector)
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<Wire>(
            self = this,
            other = other,
            { o -> this::class == o::class },
            { o -> index == o.index },
            { o -> ObjectUtils.identifierEquals(
                selfValue = parentWireVector,
                otherValue = o.parentWireVector,
                identifierAccessor = { it.identifier }
            ) }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            index,
            parentWireVector.identifier,
            this::class.java.name
        )
    }
}

class InputWire(index: Int, override val parentWireVector: InputWireVector) : Wire(index)
class OutputWire(index: Int, override val parentWireVector: OutputWireVector) : Wire(index)
