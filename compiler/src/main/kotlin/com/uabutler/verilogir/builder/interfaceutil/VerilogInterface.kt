package com.uabutler.verilogir.builder.interfaceutil

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
            name: String,
            gaplInterfaceStructure: InterfaceStructure,
        ): List<Wire> {
            return when (gaplInterfaceStructure) {
                is WireInterfaceStructure -> {
                    listOf(Wire(name, 1))
                }
                is RecordInterfaceStructure -> {
                    gaplInterfaceStructure.ports.flatMap {
                        fromGAPLInterfaceStructure("${name}_${it.key}", it.value)
                    }
                }
                is VectorInterfaceStructure -> {
                    fromGAPLInterfaceStructure(name, gaplInterfaceStructure.vectoredInterface).map {
                        Wire(it.name, it.width * gaplInterfaceStructure.size)
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