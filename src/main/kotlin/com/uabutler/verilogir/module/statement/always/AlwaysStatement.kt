package com.uabutler.verilogir.module.statement.always

import com.uabutler.verilogir.VerilogSerialize
import com.uabutler.verilogir.module.statement.expression.Expression

sealed class AlwaysStatement: VerilogSerialize

data class BlockingAssignment(
    val variableName: String,
    val expression: Expression,
): AlwaysStatement() {
    override fun verilogSerialize() = "$variableName = ${expression.verilogSerialize()};"
}

data class NonBlockingAssignment(
    val variableName: String,
    val expression: Expression,
): AlwaysStatement() {
    override fun verilogSerialize() = "$variableName <= ${expression.verilogSerialize()};"
}

data class IfBranch(
    val condition: Expression,
    val then: List<AlwaysStatement>,
)

data class IfStatement(
    val ifBranch: IfBranch,
    val elseIfBranches: List<IfBranch>,
    val elseStatements: List<AlwaysStatement>,
): AlwaysStatement() {
    override fun verilogSerialize() = buildString {
        appendLine("if (${ifBranch.condition.verilogSerialize()}) begin")
        val ifStatements = buildString { ifBranch.then.forEach { appendLine(it.verilogSerialize()) } }
        appendLine(ifStatements.prependIndent())

        val elseIfBranches = elseIfBranches.forEach { elseIfBranch ->
            appendLine("end else if (${elseIfBranch.condition.verilogSerialize()}) begin")
            val statements = buildString { elseIfBranch.then.forEach { appendLine(it.verilogSerialize()) } }
            appendLine(statements.prependIndent())
        }
        appendLine(elseIfBranches)

        appendLine("end else begin")
        val elseStatements = buildString { elseStatements.forEach { appendLine(it.verilogSerialize()) } }
        appendLine(elseStatements.prependIndent())

        appendLine("end")
    }
}