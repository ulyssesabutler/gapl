package com.uabutler.verilogir.module.statement.always

import com.uabutler.verilogir.VerilogSerialize
import com.uabutler.verilogir.module.statement.expression.Expression
import com.uabutler.verilogir.module.statement.expression.IntLiteral
import com.uabutler.verilogir.module.statement.expression.Reference

sealed class AlwaysStatement: VerilogSerialize

data class BlockingAssignment(
    val variableName: Reference,
    val expression: Expression,
): AlwaysStatement() {
    override fun verilogSerialize() = "${variableName.verilogSerialize()} = ${expression.verilogSerialize()};"
}

data class NonBlockingAssignment(
    val variableName: Reference,
    val expression: Expression,
): AlwaysStatement() {
    override fun verilogSerialize() = "${variableName.verilogSerialize()} <= ${expression.verilogSerialize()};"
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
        val ifStatements = ifBranch.then.joinToString("\n") { it.verilogSerialize() }
        appendLine(ifStatements.prependIndent())

        val elseIfBranches = elseIfBranches.forEach { elseIfBranch ->
            appendLine("end else if (${elseIfBranch.condition.verilogSerialize()}) begin")
            val statements = buildString { elseIfBranch.then.forEach { appendLine(it.verilogSerialize()) } }
            appendLine(statements.prependIndent())
        }

        appendLine("end else begin")
        val elseStatements = elseStatements.joinToString("\n") { it.verilogSerialize() }
        appendLine(elseStatements.prependIndent())

        append("end")
    }
}

data class CaseEntry(
    val condition: IntLiteral,
    val statements: List<AlwaysStatement>,
)

data class CaseStatement(
    val selector: Reference,
    val caseEntries: List<CaseEntry>,
): AlwaysStatement() {
    override fun verilogSerialize() = buildString {
        appendLine("case (${selector.verilogSerialize()})")
        val caseEntries = caseEntries.joinToString("\n") { caseEntry ->
            buildString {
                appendLine("${caseEntry.condition.value}: begin")
                val statements = caseEntry.statements.joinToString("\n") { it.verilogSerialize() }
                appendLine(statements.prependIndent())
                append("end")
            }
        }
        appendLine(caseEntries.prependIndent())
        append("endcase")
    }
}