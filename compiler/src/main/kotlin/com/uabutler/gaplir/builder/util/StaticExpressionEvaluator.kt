package com.uabutler.gaplir.builder.util

import com.uabutler.ast.node.staticexpressions.*

object StaticExpressionEvaluator {
    private fun op(
        lhs: StaticExpressionNode,
        rhs: StaticExpressionNode,
        context: Map<String, ParameterValue<*>>, // TODO: This could be any value
        op: (Int, Int) -> Int
    ): Int  {
        val lhs = evaluateStaticExpressionWithContext(lhs, context)
        val rhs = evaluateStaticExpressionWithContext(rhs, context)

        return op(lhs, rhs)
    }

    fun evaluateStaticExpressionWithContext(
        staticExpression: StaticExpressionNode,
        context: Map<String, ParameterValue<*>>, // TODO: This could be any value
    ): Int {
        return when (val e = staticExpression) {
            is TrueStaticExpressionNode -> 1
            is FalseStaticExpressionNode -> 0
            is AdditionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a + b }
            is DivisionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a / b }
            is EqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a == b)  1 else 0 }
            is GreaterThanEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a >= b) 1 else 0 }
            is GreaterThanStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a > b) 1 else 0 }
            is IntegerLiteralStaticExpressionNode -> e.integer.value
            is LessThanEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a <= b) 1 else 0 }
            is LessThanStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a < b) 1 else 0 }
            is MultiplicationStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a * b }
            is NotEqualsStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> if (a != b) 1 else 0 }
            is SubtractionStaticExpressionNode -> op(e.lhs, e.rhs, context) { a, b -> a - b }
            is IdentifierStaticExpressionNode -> {
                val identifiedValue = context[e.identifier.value]!!

                if (identifiedValue is IntegerParameterValue)
                    identifiedValue.value
                else
                    throw Exception("Parameter ${e.identifier.value} does not have integer type: $identifiedValue")
            }
        }
    }
}