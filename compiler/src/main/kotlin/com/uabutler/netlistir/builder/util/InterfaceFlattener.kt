package com.uabutler.netlistir.builder.util


data class FlatInterfaceWireVector(
    val identifier: List<String>,
    val dimensions: List<Int>,
) {
    val width: Int = dimensions.fold(1) { acc, i -> acc * i }
}

object InterfaceFlattener {
    private fun from(
        structure: InterfaceStructure,
        identifier: List<String>,
    ): List<FlatInterfaceWireVector> {
        return when (structure) {
            is WireInterfaceStructure -> {
                listOf(FlatInterfaceWireVector(identifier, listOf()))
            }
            is RecordInterfaceStructure -> {
                structure.ports.flatMap {
                    from(identifier = identifier + listOf(it.key), structure = it.value)
                }
            }
            is VectorInterfaceStructure -> {
                from(identifier = identifier, structure = structure.vectoredInterface).map {
                   FlatInterfaceWireVector(it.identifier, listOf(structure.size) + it.dimensions)
                }
            }
        }
    }

    fun fromInterfaceStructure(structure: InterfaceStructure) = from(structure, emptyList())
}