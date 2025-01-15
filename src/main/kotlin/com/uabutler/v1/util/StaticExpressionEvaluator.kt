package com.uabutler.v1.util

import com.uabutler.v1.ast.staticexpressions.*

object StaticExpressionEvaluator {
    fun evaluateConcrete(staticExpressionNode: StaticExpressionNode): Int {
        return when (staticExpressionNode) {
            is TrueStaticExpressionNode -> 1
            is FalseStaticExpressionNode -> 0
            is IntegerLiteralStaticExpressionNode -> staticExpressionNode.integer.value
            is IdentifierStaticExpressionNode -> throw Exception("Not a concrete expression")
            is AdditionStaticExpressionNode -> evaluateConcrete(staticExpressionNode.lhs) + evaluateConcrete(staticExpressionNode.rhs)
            is SubtractionStaticExpressionNode -> evaluateConcrete(staticExpressionNode.lhs) - evaluateConcrete(staticExpressionNode.rhs)
            is MultiplicationStaticExpressionNode -> evaluateConcrete(staticExpressionNode.lhs) * evaluateConcrete(staticExpressionNode.rhs)
            is DivisionStaticExpressionNode -> evaluateConcrete(staticExpressionNode.lhs) / evaluateConcrete(staticExpressionNode.rhs)
            is EqualsStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) == evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
            is NotEqualsStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) != evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
            is LessThanStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) < evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
            is GreaterThanStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) > evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
            is LessThanEqualsStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) <= evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
            is GreaterThanEqualsStaticExpressionNode -> if (evaluateConcrete(staticExpressionNode.lhs) >= evaluateConcrete(staticExpressionNode.rhs)) 1 else 0
        }
    }
}