package com.uabutler.ast.visitor

import com.uabutler.ast.node.staticexpressions.*
import com.uabutler.parsers.generated.GAPLParser

object StaticExpressionVisitor: GAPLVisitor() {

    fun visitStaticExpression(ctx: GAPLParser.StaticExpressionContext): StaticExpressionNode {
        return when (ctx) {
            is GAPLParser.TrueStaticExpressionContext -> TrueStaticExpressionNode()
            is GAPLParser.FalseStaticExpressionContext -> FalseStaticExpressionNode()
            is GAPLParser.IntLiteralStaticExpressionContext -> IntegerLiteralStaticExpressionNode(TokenVisitor.visitIntegerLiteral(ctx.IntLiteral()))
            is GAPLParser.IdStaticExpressionContext -> IdentifierStaticExpressionNode(TokenVisitor.visitId(ctx.Id()))
            is GAPLParser.ParanStaticExpressionContext -> visitStaticExpression(ctx.staticExpression())
            is GAPLParser.AddStaticExpressionContext -> AdditionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.SubtractStaticExpressionContext -> SubtractionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.MultiplyStaticExpressionContext -> MultiplicationStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.DivideStaticExpressionContext -> DivisionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.EqualsStaticExpressionContext -> EqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.NotEqualsStaticExpressionContext -> NotEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.LessThanStaticExpressionContext -> LessThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.GreaterThanStaticExpressionContext -> GreaterThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.LessThanEqualsStaticExpressionContext -> LessThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.GreaterThanEqualsStaticExpressionContext -> GreaterThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            else -> throw Exception("Unknown static expression")
        }
    }

}