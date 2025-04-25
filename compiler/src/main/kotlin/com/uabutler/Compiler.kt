package com.uabutler

import com.uabutler.gaplir.builder.ModuleBuilder
import com.uabutler.verilogir.builder.VerilogBuilder

object Compiler {
    fun compile(gapl: String): String {
        val ast = Parser.fromString(gapl).program()
        val gaplirModules = ModuleBuilder(ast).buildAllModules()
        val verilogirModules = gaplirModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) }

        return verilogirModules.joinToString("\n") { it.verilogSerialize() }
    }
}
