package com.uabutler.netlistir.builder.util

sealed interface InterfaceStructure

data object WireInterfaceStructure: InterfaceStructure

data class RecordInterfaceStructure(
    val ports: Map<String, InterfaceStructure>
): InterfaceStructure

data class VectorInterfaceStructure(
    val vectoredInterface: InterfaceStructure,
    val size: Int
): InterfaceStructure

/**
 * Total bit width if this structure collapses to a single flat leaf (a bare wire, or a Vector of
 * Vectors of ... of Wire, with no Record anywhere in the chain) - null if it's a genuine Record, or
 * a Vector wrapping one somewhere below. Shared by simgen (codegen/validation shape) and simengine
 * (runtime marshaling) so the two can never disagree about where that boundary falls.
 */
fun InterfaceStructure.flatWidth(): Int? = when (this) {
    is WireInterfaceStructure -> 1
    is VectorInterfaceStructure -> vectoredInterface.flatWidth()?.let { it * size }
    is RecordInterfaceStructure -> null
}