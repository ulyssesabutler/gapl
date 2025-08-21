package com.uabutler.cst.visitor.expression

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
import com.uabutler.cst.node.expression.CSTTrueExpression
import com.uabutler.cst.node.expression.CSTWireExpression
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.util.CSTAccessorVisitor.visitAccessor
import com.uabutler.cst.visitor.util.CSTAtomVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTExpressionVisitor: CSTVisitor() {

    fun visitExpression(cxt: CSTParser.ExpressionContext): CSTExpression {
        return when(cxt) {
            is CSTParser.AtomExpressionContext -> visitAtomExpression(cxt)
            is CSTParser.WireExpressionContext -> visitWireExpression(cxt)
            is CSTParser.TrueExpressionContext -> visitTrueExpression(cxt)
            is CSTParser.FalseExpressionContext -> visitFalseExpression(cxt)
            is CSTParser.LiteralExpressionContext -> visitLiteralExpression(cxt)
            is CSTParser.AccessorExpressionContext -> visitAccessorExpression(cxt)
            is CSTParser.ParenExpressionContext -> visitParenExpression(cxt)
            is CSTParser.MultiplicaitonExpressionContext -> visitMultiplicaitonExpression(cxt)
            is CSTParser.AdditionExpressionContext -> visitAdditionExpression(cxt)
            is CSTParser.RelationalExpressionContext -> visitRelationalExpression(cxt)
            is CSTParser.EqualityExpressionContext -> visitEqualityExpression(cxt)
            is CSTParser.LogicalAndExpressionContext -> visitLogicalAndExpression(cxt)
            is CSTParser.LogicalOrExpressionContext -> visitLogicalOrExpression(cxt)
            else -> throw IllegalArgumentException("Unexpected expression context $cxt")
        }
    }

    override fun visitAtomExpression(ctx: CSTParser.AtomExpressionContext): CSTAtomExpression {
        return CSTAtomExpression(
            atom = CSTAtomVisitor.visitAtom(ctx.atom()),
        )
    }

    override fun visitWireExpression(ctx: CSTParser.WireExpressionContext) = CSTWireExpression

    override fun visitTrueExpression(ctx: CSTParser.TrueExpressionContext) = CSTTrueExpression

    override fun visitFalseExpression(ctx: CSTParser.FalseExpressionContext) = CSTFalseExpression

    override fun visitLiteralExpression(ctx: CSTParser.LiteralExpressionContext): CSTIntLiteralExpression {
        return CSTIntLiteralExpression(
            value = visitIntLiteral(ctx.value)
        )
    }

    override fun visitAccessorExpression(ctx: CSTParser.AccessorExpressionContext): CSTAccessorExpression {
        return CSTAccessorExpression(
            accessed = visitExpression(ctx.expression()),
            accessor = visitAccessor(ctx.accessor()),
        )
    }

    override fun visitParenExpression(ctx: CSTParser.ParenExpressionContext): CSTExpression {
        return visitExpression(ctx.expression())
    }

    override fun visitMultiplicaitonExpression(ctx: CSTParser.MultiplicaitonExpressionContext): CSTExpression {
        return when (val operation = operatorFrom(ctx.op)) {
            Companion.Operator.MULTIPLY -> CSTMultiplicationExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.DIVIDE -> CSTDivisionExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            else -> throw IllegalArgumentException("Unexpected operator $operation")
        }
    }

    override fun visitAdditionExpression(ctx: CSTParser.AdditionExpressionContext): CSTExpression {
        return when (val operation = operatorFrom(ctx.op)) {
            Companion.Operator.ADD -> CSTAdditionExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.SUBTRACT -> CSTDivisionExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            else -> throw IllegalArgumentException("Unexpected operator $operation")
        }
    }

    override fun visitRelationalExpression(ctx: CSTParser.RelationalExpressionContext): CSTExpression {
        return when (val operation = operatorFrom(ctx.op)) {
            Companion.Operator.LESS_THAN -> CSTLessThanExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.GREATER_THAN -> CSTGreaterThanExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.LESS_THAN_EQUALS -> CSTLessThanOrEqualsExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.GREATER_THAN_EQUALS -> CSTGreaterThanOrEqualsExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            else -> throw IllegalArgumentException("Unexpected operator $operation")
        }
    }

    override fun visitEqualityExpression(ctx: CSTParser.EqualityExpressionContext): CSTExpression {
        return when (val operation = operatorFrom(ctx.op)) {
            Companion.Operator.EQUALS -> CSTEqualsExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            Companion.Operator.NOT_EQUALS -> CSTNotEqualsExpression(
                lhs = visitExpression(ctx.lhs!!),
                rhs = visitExpression(ctx.rhs!!),
            )
            else -> throw IllegalArgumentException("Unexpected operator $operation")
        }
    }

    override fun visitLogicalAndExpression(ctx: CSTParser.LogicalAndExpressionContext): CSTExpression {
        return CSTLogicalAndExpression(
            lhs = visitExpression(ctx.lhs!!),
            rhs = visitExpression(ctx.rhs!!),
        )
    }

    override fun visitLogicalOrExpression(ctx: CSTParser.LogicalOrExpressionContext): CSTExpression {
        return CSTLogicalOrExpression(
            lhs = visitExpression(ctx.lhs!!),
            rhs = visitExpression(ctx.rhs!!),
        )
    }

}