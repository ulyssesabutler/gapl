package com.uabutler.v2.verilogir

import com.uabutler.v2.verilogir.module.Module

data class Verilog(
    val modules: List<Module>,
): VerilogSerialize {
    override fun verilogSerialize() = buildString { modules.forEach { appendLine(it) } }
}