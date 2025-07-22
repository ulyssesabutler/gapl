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

class InputWire(
    index: Int,
    override val parentWireVector: InputWireVector,
    val source: OutputWire? = null
) : Wire(index) {
    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf("index" to index),
            identityProps = mapOf(
                "parentWireVector" to parentWireVector,
                "source" to source
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<InputWire>(
            self = this,
            other = other,
            { o -> super.equals(o) },
            { o -> source?.let { s1 ->
                o.source?.let { s2 ->
                    s1.index == s2.index && 
                    ObjectUtils.identifierEquals(
                        selfValue = s1.parentWireVector,
                        otherValue = s2.parentWireVector,
                        identifierAccessor = { it.identifier }
                    )
                } ?: (source == null)
            } ?: (o.source == null) }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            super.hashCode(),
            source?.index,
            source?.parentWireVector?.identifier
        )
    }
}

class OutputWire(index: Int, override val parentWireVector: OutputWireVector) : Wire(index)
