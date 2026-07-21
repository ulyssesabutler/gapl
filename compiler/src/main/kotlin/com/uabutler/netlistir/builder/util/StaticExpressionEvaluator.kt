package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.staticexpressions.*
import com.uabutler.diagnostics.BuilderDiagnosticKind
import java.math.BigInteger

object StaticExpressionEvaluator {
    private fun op(
        lhs: StaticExpressionNode,
        rhs: StaticExpressionNode,
        context: Map<String, ParameterValue<*>>, // TODO: This could be any value
        op: (BigInteger, BigInteger) -> BigInteger
    ): BigInteger  {
        val lhs = evaluateStaticExpressionWithContext(lhs, context)
        val rhs = evaluateStaticExpressionWithContext(rhs, context)

        return op(lhs, rhs)
    }

    private fun conditional(boolean: Boolean): BigInteger {
        return if (boolean) BigInteger.ONE else BigInteger.ZERO
    }

    fun evaluateStaticExpressionWithContext(
        staticExpression: StaticExpressionNode,
        context: Map<String, ParameterValue<*>>, // TODO: This could be any value
    ): BigInteger {
        return when (val e = staticExpression) {
            is TrueStaticExpressionNode -> BigInteger.ONE
            is FalseStaticExpressionNode -> BigInteger.ZERO
            is AdditionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a + b }
            is DivisionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a / b }
            is RemainderStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a % b }
            is EqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a == b) }
            is GreaterThanEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a >= b) }
            is GreaterThanStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a > b) }
            is IntegerLiteralStaticExpressionNode -> e.integer.value
            is LessThanEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a <= b) }
            is LessThanStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a < b) }
            is MultiplicationStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a * b }
            is NotEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> conditional(a != b) }
            is SubtractionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a - b }
            is IdentifierStaticExpressionNode -> {
                val identifiedValue = try {
                    context[e.identifier.value]!!
                } catch (_: NullPointerException) {
                    throw BuilderDiagnosticException(BuilderDiagnosticKind.UnableToFindStaticExpressionValue(e.identifier.value), e.span)
                }

                if (identifiedValue is IntegerParameterValue)
                    identifiedValue.value
                else
                    throw BuilderDiagnosticException(
                        BuilderDiagnosticKind.StaticExpressionParameterNotInteger(e.identifier.value),
                        e.span,
                    )
            }
            is ErrorStaticExpressionNode -> throw IllegalStateException(
                "Reached NodeBuilder with an error node (${e.message}) that should have been caught by semantic analysis - this is a compiler bug"
            )
        }
    }
}