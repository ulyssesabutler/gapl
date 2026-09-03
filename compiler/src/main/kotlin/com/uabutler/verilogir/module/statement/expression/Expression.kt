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

data class IntLiteral(
    val value: BigInteger,
    /**
     * Width of the constant, when it has one.
     *
     * An unsized Verilog constant takes its width from its value, so a wide literal whose top bits
     * happen to be zero silently narrows: a 2048-bit constant with three leading zeros is a 2045-bit
     * constant, and Verilator rejects the assignment. Emitting the width makes the constant say how
     * wide it is instead of leaving it to be inferred.
     */
    val width: Int? = null,
): Expression() {
    override fun verilogSerialize() =
        if (width == null) value.toString()
        // Masked because a Verilog sized constant is unsigned; a negative value would otherwise
        // serialise with a leading '-'.
        else "$width'h${(value and ((BigInteger.ONE shl width) - BigInteger.ONE)).toString(16)}"
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