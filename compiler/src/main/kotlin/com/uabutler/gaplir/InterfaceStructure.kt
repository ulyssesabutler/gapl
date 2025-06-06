package com.uabutler.gaplir

sealed interface InterfaceStructure

data object WireInterfaceStructure: InterfaceStructure

data class RecordInterfaceStructure(
    val ports: Map<String, InterfaceStructure>
): InterfaceStructure

data class VectorInterfaceStructure(
    val vectoredInterface: InterfaceStructure,
    val size: Int
): InterfaceStructure