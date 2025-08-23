package com.uabutler.resolver.scope.staticexpressions

import com.uabutler.ast.node.IntegerLiteralNode
import com.uabutler.ast.node.staticexpressions.AdditionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.DivisionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.EqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.FalseStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.GreaterThanEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.GreaterThanStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.IdentifierStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.IntegerLiteralStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.LessThanEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.LessThanStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.MultiplicationStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.NotEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.ast.node.staticexpressions.SubtractionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.TrueStaticExpressionNode
import com.uabutler.cst.node.expression.CSTAccessorExpression
import com.uabutler.cst.node.expression.CSTAdditionExpression
import com.uabutler.cst.node.expression.CSTAtomExpression
import com.uabutler.cst.node.expression.CSTDivisionExpression
import com.uabutler.cst.node.expression.CSTEqualsExpression
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.expression.CSTFalseExpression
import com.uabutler.cst.node.expression.CSTGreaterThanExpression
import com.uabutler.cst.node.expression.CSTGreaterThanOrEqualsExpression
import com.uabutler.cst.node.expression.CSTIntLiteralExpression
import com.uabutler.cst.node.expression.CSTLessThanExpression
import com.uabutler.cst.node.expression.CSTLessThanOrEqualsExpression
import com.uabutler.cst.node.expression.CSTLogicalAndExpression
import com.uabutler.cst.node.expression.CSTLogicalOrExpression
import com.uabutler.cst.node.expression.CSTMultiplicationExpression
import com.uabutler.cst.node.expression.CSTNotEqualsExpression
import com.uabutler.cst.node.expression.CSTParenthesizedExpression
import com.uabutler.cst.node.expression.CSTSubtractionExpression
import com.uabutler.cst.node.expression.CSTTrueExpression
import com.uabutler.cst.node.expression.CSTWireExpression
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier

class StaticExpressionScope(
    parentScope: Scope,
    val staticExpression: CSTExpression,
): Scope by parentScope {

    fun ast(): StaticExpressionNode {
        return when (staticExpression) {
            is CSTAtomExpression -> {
                if (staticExpression.atom.parameterValues.isNotEmpty())
                    throw IllegalArgumentException("Unexpected use of ${staticExpression.atom.identifier} in static expression")

                IdentifierStaticExpressionNode(staticExpression.atom.identifier.toIdentifier())
            }

            is CSTTrueExpression -> TrueStaticExpressionNode()

            is CSTFalseExpression -> FalseStaticExpressionNode()

            is CSTIntLiteralExpression -> IntegerLiteralStaticExpressionNode(IntegerLiteralNode(staticExpression.value))

            is CSTParenthesizedExpression -> StaticExpressionScope(this, staticExpression.expression).ast()

            is CSTMultiplicationExpression -> MultiplicationStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTDivisionExpression -> DivisionStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTAdditionExpression -> AdditionStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTSubtractionExpression -> SubtractionStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTLessThanExpression -> LessThanStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTGreaterThanExpression -> GreaterThanStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTLessThanOrEqualsExpression -> LessThanEqualsStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTGreaterThanOrEqualsExpression -> GreaterThanEqualsStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTEqualsExpression -> EqualsStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTNotEqualsExpression -> NotEqualsStaticExpressionNode(
                lhs = StaticExpressionScope(this, staticExpression.lhs).ast(),
                rhs = StaticExpressionScope(this, staticExpression.rhs).ast(),
            )

            is CSTLogicalAndExpression -> TODO()

            is CSTLogicalOrExpression -> TODO()

            is CSTAccessorExpression -> throw Exception("Unexpected accessor expression in static expression")
            CSTWireExpression -> throw Exception("Unexpected use of wire in static expression")
        }
    }
}