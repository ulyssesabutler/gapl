package com.uabutler.visitor

import com.uabutler.ast.staticexpressions.*
import com.uabutler.parsers.generated.GAPLParser

object StaticExpressionVisitor: GAPLVisitor() {

    fun visitStaticExpression(ctx: GAPLParser.StaticExpressionContext): StaticExpressionNode {
        return when (ctx) {
            is GAPLParser.TrueStaticExpressionContext -> TrueStaticExpressionNode()
            is GAPLParser.FalseStaticExpressionContext -> FalseStaticExpressionNode()
            is GAPLParser.IntLiteralStaticExpressionContext -> {
                val current = IntegerLiteralStaticExpressionNode(TokenVisitor.visitIntegerLiteral(ctx.IntLiteral()))
                current.integer.parent = current
                current
            }
            is GAPLParser.IdStaticExpressionContext -> {
                val current = IdentifierStaticExpressionNode(TokenVisitor.visitId(ctx.Id()))
                current.identifier.parent = current
                current
            }
            is GAPLParser.ParanStaticExpressionContext -> visitStaticExpression(ctx.staticExpression())
            is GAPLParser.AddStaticExpressionContext -> {
                val current = AdditionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.SubtractStaticExpressionContext -> {
                val current = SubtractionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.MultiplyStaticExpressionContext -> {
                val current = MultiplicationStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.DivideStaticExpressionContext -> {
                val current = DivisionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.EqualsStaticExpressionContext -> {
                val current = EqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.NotEqualsStaticExpressionContext -> {
                val current = NotEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.LessThanStaticExpressionContext -> {
                val current = LessThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.GreaterThanStaticExpressionContext -> {
                val current = GreaterThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.LessThanEqualsStaticExpressionContext -> {
                val current = LessThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            is GAPLParser.GreaterThanEqualsStaticExpressionContext -> {
                val current = GreaterThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
                current.lhs.parent = current
                current.rhs.parent = current
                current
            }
            else -> throw Exception("Unknown static expression")
        }
    }

}