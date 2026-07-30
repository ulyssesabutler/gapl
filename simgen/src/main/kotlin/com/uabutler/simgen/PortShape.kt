package com.uabutler.simgen

import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.RecordInterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.builder.util.flatWidth

/**
 * A port's Kotlin-relevant shape: a flat wire[N] leaf, an array of some element shape, or a record
 * of named sub-shapes. Built on top of InterfaceStructure.flatWidth() so it can never disagree with
 * Engine's own runtime marshaling (simengine.PortValue) about where a leaf collapses.
 */
sealed interface PortShape {
    data class Leaf(val width: Int) : PortShape
    data class Vector(val element: PortShape, val size: Int) : PortShape
    data class Record(val fields: Map<String, PortShape>) : PortShape

    companion object {
        fun fromInterfaceStructure(structure: InterfaceStructure): PortShape {
            structure.flatWidth()?.let { return Leaf(it) }
            return when (structure) {
                is RecordInterfaceStructure -> Record(structure.ports.mapValues { fromInterfaceStructure(it.value) })
                is VectorInterfaceStructure -> Vector(fromInterfaceStructure(structure.vectoredInterface), structure.size)
                is WireInterfaceStructure -> error("unreachable: flatWidth() is never null for a bare wire")
            }
        }
    }
}
