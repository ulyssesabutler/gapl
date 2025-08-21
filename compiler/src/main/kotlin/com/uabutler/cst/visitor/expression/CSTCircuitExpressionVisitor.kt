package com.uabutler.cst.visitor.expression

import com.uabutler.cst.node.expression.CSTCircuitExpression
import com.uabutler.cst.node.expression.CSTCircuitGroupExpression
import com.uabutler.cst.node.expression.CSTCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTDeclaredCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTLoneCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTParenthesesCircuitNodeExpression
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.util.CSTCircuitExpressionTypeVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTCircuitExpressionVisitor: CSTVisitor() {

    override fun visitCircuitExpression(ctx: CSTParser.CircuitExpressionContext): CSTCircuitExpression {
        return CSTCircuitExpression(
            connectedGroups = ctx.circuitGroupExpression().map { visitCircuitGroupExpression(it) }
        )
    }

    override fun visitCircuitGroupExpression(ctx: CSTParser.CircuitGroupExpressionContext): CSTCircuitGroupExpression {
        return CSTCircuitGroupExpression(
            groupedNodes = ctx.circuitNodeExpression().map { visitCircuitNodeExpression(it) }
        )
    }

    fun visitCircuitNodeExpression(ctx: CSTParser.CircuitNodeExpressionContext): CSTCircuitNodeExpression {
        return when (ctx) {
            is CSTParser.LoneCircuitExpressionContext -> visitLoneCircuitExpression(ctx)
            is CSTParser.DeclaredCircuitExpressionContext -> visitDeclaredCircuitExpression(ctx)
            is CSTParser.ParenCircuitExpressionContext -> visitParenCircuitExpression(ctx)
            else -> throw IllegalArgumentException("Unexpected circuit expression context $ctx")
        }
    }

    override fun visitLoneCircuitExpression(ctx: CSTParser.LoneCircuitExpressionContext): CSTLoneCircuitNodeExpression {
        return CSTLoneCircuitNodeExpression(
            expression = CSTExpressionVisitor.visitExpression(ctx.expression())
        )
    }

    override fun visitDeclaredCircuitExpression(ctx: CSTParser.DeclaredCircuitExpressionContext): CSTDeclaredCircuitNodeExpression {
        return CSTDeclaredCircuitNodeExpression(
            declaredIdentifier = visitId(ctx.declaredIdentifier),
            type = CSTCircuitExpressionTypeVisitor.visitCircuitExpressionType(ctx.circuitExpressionType()),
        )
    }

    override fun visitParenCircuitExpression(ctx: CSTParser.ParenCircuitExpressionContext): CSTParenthesesCircuitNodeExpression {
        return CSTParenthesesCircuitNodeExpression(
            expression = visitCircuitExpression(ctx.circuitExpression())
        )
    }

}