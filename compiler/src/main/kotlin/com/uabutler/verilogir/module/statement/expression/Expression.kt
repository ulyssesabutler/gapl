package com.uabutler.verilogir.module.statement.expression

import com.uabutler.verilogir.VerilogSerialize
import com.uabutler.verilogir.module.statement.util.BinaryOperator
import com.uabutler.verilogir.module.statement.util.UnaryOperator
import java.math.BigInteger


sealed class Expression: VerilogSerialize

data class Reference(
    val variableName: String,
    val startIndex: Int? = null,
    val endIndex: Int? = null,
): Expression() {
    init {
        if (startIndex != null && endIndex != null && startIndex < endIndex) {
            throw IllegalArgumentException("Failed to create reference for $variableName: start index ($startIndex) must be greater than or equal to end index ($endIndex).")
        }
    }
    override fun verilogSerialize() = buildString {
        append(variableName)
        if (startIndex != null && endIndex != null) {
            append("[$startIndex:$endIndex]")
        }
    }
}

/**
 * A decimal integer literal. With [width] set, it is emitted sized (`64'd...`); without, unsized.
 * Verilog treats an unsized decimal as a 32-bit number, so any literal whose value may not fit in 32
 * bits must carry its width.
 */
data class IntLiteral(
    val value: BigInteger,
    val width: Int? = null,
): Expression() {
    override fun verilogSerialize() = if (width != null) "$width'd$value" else value.toString()
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