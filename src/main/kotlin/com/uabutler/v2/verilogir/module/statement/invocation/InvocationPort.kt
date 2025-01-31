package com.uabutler.v2.verilogir.module.statement.invocation

import com.uabutler.v2.verilogir.VerilogSerialize

data class InvocationPort(
    val modulePortName: String,
    val variablePortName: String,
): VerilogSerialize {
    override fun verilogSerialize() = buildString {
        append(".")
        append(modulePortName)
        append("(")
        append(variablePortName)
        append(")")
    }
}