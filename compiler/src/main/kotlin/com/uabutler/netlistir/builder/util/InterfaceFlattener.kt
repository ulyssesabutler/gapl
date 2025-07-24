package com.uabutler.netlistir.builder.util


data class FlatInterfaceWireVector(
    val identifier: List<String>,
    val dimensions: List<Int>,
) {
    val width: Int = dimensions.reduce { acc, i -> acc * i }
}

 object InterfaceFlattener {
    fun fromInterfaceStructure(
        structure: InterfaceStructure,
        identifier: List<String> = emptyList(),
    ): List<FlatInterfaceWireVector> {
        return when (structure) {
            is WireInterfaceStructure -> {
                listOf(FlatInterfaceWireVector(identifier, listOf(1)))
            }
            is RecordInterfaceStructure -> {
                structure.ports.flatMap {
                    fromInterfaceStructure(identifier = identifier + it.key, structure = it.value)
                }
            }
            is VectorInterfaceStructure -> {
                fromInterfaceStructure(identifier = identifier, structure = structure.vectoredInterface).map {
                   FlatInterfaceWireVector(it.identifier, listOf(structure.size) + it.dimensions)
                }
            }
        }
    }
}