package com.uabutler.resolver.scope.staticexpressions

import com.uabutler.ast.node.IntegerLiteralNode
import com.uabutler.ast.node.staticexpressions.AdditionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.DivisionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.EqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.ErrorStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.FalseStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.GreaterThanEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.GreaterThanStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.IdentifierStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.IntegerLiteralStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.LessThanEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.LessThanStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.MultiplicationStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.NotEqualsStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.RemainderStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.ast.node.staticexpressions.SubtractionStaticExpressionNode
import com.uabutler.ast.node.staticexpressions.TrueStaticExpressionNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTLexer
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.util.toIdentifierNode

class StaticExpressionScope(
    parentScope: Scope,
    val staticExpression: CSTParser.ExpressionContext,
): Scope by parentScope {

    fun ast(): StaticExpressionNode {
        val span = SourceSpan.of(staticExpression)

        fun lhs(ctx: CSTParser.ExpressionContext) = StaticExpressionScope(this, ctx).ast()
        fun rhs(ctx: CSTParser.ExpressionContext) = StaticExpressionScope(this, ctx).ast()

        return when (staticExpression) {
            is CSTParser.AtomExpressionContext -> {
                val atom = staticExpression.atom()!!

                if (atom.parameterValues() != null) {
                    diagnostics.reportError("Unexpected parameters for '${atom.identifier!!.text}' in static expression", span)
                    return ErrorStaticExpressionNode(span, "unexpected parameters")
                }

                IdentifierStaticExpressionNode(span, atom.identifier!!.toIdentifierNode())
            }

            is CSTParser.TrueExpressionContext -> TrueStaticExpressionNode(span)

            is CSTParser.FalseExpressionContext -> FalseStaticExpressionNode(span)

            is CSTParser.LiteralExpressionContext -> IntegerLiteralStaticExpressionNode(
                span,
                IntegerLiteralNode(span, staticExpression.value!!.text!!.toBigInteger()),
            )

            is CSTParser.ParenExpressionContext -> StaticExpressionScope(this, staticExpression.expression()!!).ast()

            is CSTParser.MultiplicaitonExpressionContext -> when (staticExpression.op?.type) {
                CSTLexer.Tokens.Multiply -> MultiplicationStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.Divide -> DivisionStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.Remainder -> RemainderStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                else -> throw IllegalStateException("Unexpected operator ${staticExpression.op}")
            }

            is CSTParser.AdditionExpressionContext -> when (staticExpression.op?.type) {
                CSTLexer.Tokens.Add -> AdditionStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.Subtract -> SubtractionStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                else -> throw IllegalStateException("Unexpected operator ${staticExpression.op}")
            }

            is CSTParser.RelationalExpressionContext -> when (staticExpression.op?.type) {
                CSTLexer.Tokens.AngleL -> LessThanStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.AngleR -> GreaterThanStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.LessThanEquals -> LessThanEqualsStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.GreaterThanEquals -> GreaterThanEqualsStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                else -> throw IllegalStateException("Unexpected operator ${staticExpression.op}")
            }

            is CSTParser.EqualityExpressionContext -> when (staticExpression.op?.type) {
                CSTLexer.Tokens.Equals -> EqualsStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                CSTLexer.Tokens.NotEquals -> NotEqualsStaticExpressionNode(span, lhs(staticExpression.lhs!!), rhs(staticExpression.rhs!!))
                else -> throw IllegalStateException("Unexpected operator ${staticExpression.op}")
            }

            is CSTParser.LogicalAndExpressionContext, is CSTParser.LogicalOrExpressionContext -> {
                diagnostics.reportError("Logical && / || are not supported in static expressions yet", span)
                ErrorStaticExpressionNode(span, "unsupported logical operator")
            }

            is CSTParser.AccessorExpressionContext -> {
                diagnostics.reportError("Unexpected accessor expression in static expression", span)
                ErrorStaticExpressionNode(span, "unexpected accessor")
            }

            is CSTParser.WireExpressionContext -> {
                diagnostics.reportError("Unexpected use of 'wire' in static expression", span)
                ErrorStaticExpressionNode(span, "unexpected wire")
            }

            else -> throw IllegalStateException("Unexpected static expression context $staticExpression")
        }
    }
}
