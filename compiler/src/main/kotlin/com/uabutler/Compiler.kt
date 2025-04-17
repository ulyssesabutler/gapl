package com.uabutler

import com.uabutler.gaplir.builder.ModuleBuilder
import com.uabutler.verilogir.builder.VerilogBuilder

object Compiler {
    fun compile(gapl: String): String {
        print("Computing AST: ")
        val ast = Parser.fromString(gapl).program()
        println(ast)

        print("Computing gapl IR: ")
        val gaplirModules = ModuleBuilder(ast).buildAllModules()
        println(gaplirModules)

        print("Computing Verilog IR: ")
        val verilogirModules = gaplirModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) }
        println(verilogirModules)

        return verilogirModules.joinToString("\n") { it.verilogSerialize() }
    }
}
