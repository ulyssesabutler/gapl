package com.uabutler.verilogir.module

import com.uabutler.verilogir.VerilogSerialize
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.util.DataType

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

        val clock = ModuleIO(
            name = "clock",
            direction = ModuleIODirection.INPUT,
            type = DataType.WIRE,
            startIndex = null,
            endIndex = null,
        )

        val reset = ModuleIO(
            name = "reset",
            direction = ModuleIODirection.INPUT,
            type = DataType.WIRE,
            startIndex = null,
            endIndex = null,
        )

        val enable = ModuleIO(
            name = "enable",
            direction = ModuleIODirection.INPUT,
            type = DataType.WIRE,
            startIndex = null,
            endIndex = null,
        )

        // Module Ports
        val io = (listOf(clock, reset, enable) + inputs + outputs).joinToString(",\n") { it.verilogSerialize() }
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
    val startIndex: Int?,
    val endIndex: Int?,
): VerilogSerialize {
    override fun verilogSerialize() = buildString {
        append(direction.verilog)
        append(" ")
        append(type.verilog)
        if (startIndex != null && endIndex != null) {
            append(" [$startIndex:$endIndex] ")
        } else {
            append(" ")
        }
        append(name)
    }
}