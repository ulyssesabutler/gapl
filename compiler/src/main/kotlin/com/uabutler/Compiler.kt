package com.uabutler

import com.uabutler.netlistir.builder.ModuleBuilder
import com.uabutler.netlistir.transformer.Transformer
import com.uabutler.verilogir.builder.VerilogBuilder

object Compiler {
    fun compile(gapl: String): String {
        val ast = Parser.fromString(gapl).program()
        val netlistModules = ModuleBuilder(ast).buildAllModules()
        val transformedModules = Transformer.allTransformations(netlistModules)
        val verilogirModules = transformedModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) }

        return verilogirModules.joinToString("\n") { it.verilogSerialize() }
    }
}
