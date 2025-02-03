package com.uabutler.v2.verilogir.module

import com.uabutler.v2.verilogir.VerilogSerialize
import com.uabutler.v2.verilogir.module.statement.Statement
import com.uabutler.v2.verilogir.util.DataType

data class Module(
    val name: String,
    // TODO: Parameters
    val inputs: List<ModuleIO>,
    val outputs: List<ModuleIO>,
    val statements: List<Statement>,
): VerilogSerialize {
    override fun verilogSerialize() = buildString {
        appendLine("module $name")
        appendLine("(")

        // Module Ports
        val io = (inputs + outputs).joinToString(",\n") { it.verilogSerialize() }
        appendLine(io.prependIndent())

        appendLine(");")

        // Module Statements
        val statements = statements.joinToString("\n") { it.verilogSerialize() }
        appendLine(statements.prependIndent())

        appendLine("endmodule")
    }
}

enum class ModuleIODirection(val verilog: String) {
    INPUT("input"),
    OUTPUT("output"),
}

data class ModuleIO(
    val name: String,
    val direction: ModuleIODirection,
    val type: DataType,
    val startIndex: Int,
    val endIndex: Int,
): VerilogSerialize {
    override fun verilogSerialize() = buildString {
        append(direction.verilog)
        append(" ")
        append(type.verilog)
        append(" [$startIndex:$endIndex] ")
        append(name)
    }
}