package com.uabutler.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.RecordInterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure

class VerilogInterface(
    val name: String,
    val gaplInterfaceStructure: InterfaceStructure,
) {

    companion object {
        fun fromGAPLInterfaceStructure(
            structure: InterfaceStructure,
            name: String? = null,
        ): List<Wire> {
            return when (structure) {
                is WireInterfaceStructure -> {
                    listOf(Wire(name ?: "", 1))
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
                        Wire(it.name, it.width * structure.size)
                    }
                }
            }
        }
    }

    data class Wire(
        val name: String,
        val width: Int,
    )

}