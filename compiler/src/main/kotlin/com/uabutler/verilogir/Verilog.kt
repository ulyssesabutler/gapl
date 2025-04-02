package com.uabutler.verilogir

import com.uabutler.verilogir.module.Module

data class Verilog(
    val modules: List<Module>,
): VerilogSerialize {
    override fun verilogSerialize() = buildString { modules.forEach { appendLine(it) } }
}