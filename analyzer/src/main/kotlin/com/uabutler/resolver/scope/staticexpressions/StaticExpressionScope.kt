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
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTLexer
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.semanticTokenKind
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
                    diagnostics.reportError(ResolverDiagnosticKind.UnexpectedStaticExpressionParameters(atom.identifier!!.text!!), span)
                    return ErrorStaticExpressionNode(span, "unexpected parameters")
                }

                val identifier = atom.identifier!!
                // Static expressions (vector bounds, generic parameter values) don't go through
                // Scope.resolve() - resolution failures here are handled later, during
                // netlist-building. Use resolveGlobal directly so a valid reference still gets
                // classified for semantic tokens, without resolve()'s diagnostic-on-failure side
                // effect changing this stage's error behavior.
                resolveGlobal(identifier.text!!)?.let { semanticTokens.record(SourceSpan.of(identifier), semanticTokenKind(it)) }

                IdentifierStaticExpressionNode(span, identifier.toIdentifierNode())
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
                diagnostics.reportError(ResolverDiagnosticKind.UnsupportedLogicalOperatorsInStaticExpression, span)
                ErrorStaticExpressionNode(span, "unsupported logical operator")
            }

            is CSTParser.AccessorExpressionContext -> {
                diagnostics.reportError(ResolverDiagnosticKind.UnexpectedAccessorInStaticExpression, span)
                ErrorStaticExpressionNode(span, "unexpected accessor")
            }

            is CSTParser.WireExpressionContext -> {
                diagnostics.reportError(ResolverDiagnosticKind.UnexpectedWireInStaticExpression, span)
                ErrorStaticExpressionNode(span, "unexpected wire")
            }

            else -> throw IllegalStateException("Unexpected static expression context $staticExpression")
        }
    }
}
