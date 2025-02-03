package com.uabutler.v2.verilogir.builder.`interface`

import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.RecordInterfaceStructure
import com.uabutler.v2.gaplir.VectorInterfaceStructure
import com.uabutler.v2.gaplir.WireInterfaceStructure

class VerilogInterface(
    val name: String,
    val gaplInterfaceStructure: InterfaceStructure,
) {

    companion object {
        fun fromGAPLInterfaceStructure(
            name: String,
            gaplInterfaceStructure: InterfaceStructure
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
                        Wire(name, it.width * gaplInterfaceStructure.size)
                    }
                }
            }
        }
    }

    data class Wire(
        val name: String,
        val width: Int,
    )

    val wires: List<Wire> = fromGAPLInterfaceStructure(name, gaplInterfaceStructure)

}