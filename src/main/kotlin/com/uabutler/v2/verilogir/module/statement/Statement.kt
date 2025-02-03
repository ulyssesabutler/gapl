package com.uabutler.v2.verilogir.module.statement

import com.uabutler.v2.verilogir.VerilogSerialize
import com.uabutler.v2.verilogir.module.statement.always.AlwaysStatement
import com.uabutler.v2.verilogir.module.statement.always.Sensitivity
import com.uabutler.v2.verilogir.module.statement.expression.Expression
import com.uabutler.v2.verilogir.module.statement.expression.Reference
import com.uabutler.v2.verilogir.module.statement.invocation.InvocationPort
import com.uabutler.v2.verilogir.util.DataType
import javax.swing.plaf.nimbus.State

sealed class Statement: VerilogSerialize

data class Declaration(
    val name: String,
    val type: DataType,
    val startIndex: Int,
    val endIndex: Int,
): Statement() {
    override fun verilogSerialize() = buildString {
        append(type.verilog)
        append(" [$startIndex:$endIndex] ")
        append(name)
        append(";")
    }
}

data class Assignment(
    val destReference: Reference,
    val expression: Expression,
): Statement() {
    override fun verilogSerialize() = buildString {
        append("assign ")
        append(destReference.verilogSerialize())
        append(" = ")
        append(expression.verilogSerialize())
        append(";")
    }
}

data class Invocation(
    val invocationName: String,
    val moduleName: String,
    // TODO: Parameters
    val ports: List<InvocationPort>,
): Statement() {
    override fun verilogSerialize() = buildString {
        appendLine("$moduleName $invocationName")
        appendLine("(")

        val ports = ports.joinToString(",\n") { it.verilogSerialize() }
        appendLine(ports.prependIndent())

        appendLine(");")
    }
}

data class Always(
    val sensitivity: Sensitivity,
    val statements: List<AlwaysStatement>,
): Statement() {
    override fun verilogSerialize() = buildString {
        appendLine("always @(${sensitivity.verilogSerialize()} begin")

        val statements = statements.joinToString("\n") { it.verilogSerialize() }
        appendLine(statements.prependIndent())

        appendLine("end")
    }
}
