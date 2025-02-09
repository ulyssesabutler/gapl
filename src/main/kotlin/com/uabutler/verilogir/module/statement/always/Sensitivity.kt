package com.uabutler.verilogir.module.statement.always

import com.uabutler.verilogir.VerilogSerialize

sealed class Sensitivity: VerilogSerialize

data object Combinational: Sensitivity() {
    override fun verilogSerialize() = "*"
}

data class Clocked(
    val clockName: String,
): Sensitivity() {
    override fun verilogSerialize() = "posedge $clockName"
}