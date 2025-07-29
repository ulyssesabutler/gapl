package com.uabutler.verilogir.module.statement.expression

import com.uabutler.verilogir.VerilogSerialize
import com.uabutler.verilogir.module.statement.util.BinaryOperator
import com.uabutler.verilogir.module.statement.util.UnaryOperator


sealed class Expression: VerilogSerialize

data class Reference(
    val variableName: String,
    val startIndex: Int? = null,
    val endIndex: Int? = null,
): Expression() {
    init {
        if (startIndex != null && endIndex != null && startIndex < endIndex) {
            throw IllegalArgumentException("The start index ($startIndex) must be greater than or equal to end index ($endIndex). [$startIndex:$endIndex]")
        }
    }
    override fun verilogSerialize() = buildString {
        append(variableName)
        if (startIndex != null && endIndex != null) {
            append("[$startIndex:$endIndex]")
        }
    }
}

data class IntLiteral(
    val value: Int,
): Expression() {
    override fun verilogSerialize() = value.toString()
}

data class BinaryOperation(
    val lhs: Expression,
    val rhs: Expression,
    val operator: BinaryOperator,
): Expression() {
    override fun verilogSerialize() = buildString {
        // TODO: How many parentheses should we add? YES!
        append("(")
        append(lhs.verilogSerialize())
        append(" ${operator.verilog} ")
        append(rhs.verilogSerialize())
        append(")")
    }
}

data class UnaryOperation(
    val operand: Expression,
    val operator: UnaryOperator,
): Expression() {
    override fun verilogSerialize() = buildString {
        // TODO: How many parentheses should we add? YES!
        append("(")
        append(operator.verilog)
        append(operand.verilogSerialize())
        append(")")
    }
}