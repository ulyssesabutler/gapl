package com.uabutler.netlistir.builder.util


data class FlatInterfaceWireVector(
    val name: String,
    val width: Int,
)

class InterfaceFlattener(
    val name: String,
    val gaplInterfaceStructure: InterfaceStructure,
) {

    companion object {
        fun fromGAPLInterfaceStructure(
            structure: InterfaceStructure,
            name: String? = null,
        ): List<FlatInterfaceWireVector> {
            return when (structure) {
                is WireInterfaceStructure -> {
                    listOf(FlatInterfaceWireVector(name ?: "", 1))
                }
                is RecordInterfaceStructure -> {
                    structure.ports.flatMap {
                        if (name == null) {
                            fromGAPLInterfaceStructure(name = it.key, structure = it.value)
                        } else {
                            fromGAPLInterfaceStructure(name = "${name}_${it.key}", structure = it.value)
                        }
                    }
                }
                is VectorInterfaceStructure -> {
                    fromGAPLInterfaceStructure(name = name, structure = structure.vectoredInterface).map {
                       FlatInterfaceWireVector(it.name, it.width * structure.size)
                    }
                }
            }
        }
    }

}