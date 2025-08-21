package com.uabutler.cst.visitor.functions

import com.uabutler.cst.node.functions.CSTCircuitStatement
import com.uabutler.cst.node.functions.CSTConditional
import com.uabutler.cst.node.functions.CSTConditionalCircuitBody
import com.uabutler.cst.node.functions.CSTConditionalCircuitStatement
import com.uabutler.cst.node.functions.CSTNonConditionalCircuitStatement
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTCircuitExpressionVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTCircuitStatementVisitor: CSTVisitor() {

    fun visitCircuitStatement(ctx: CSTParser.CircuitStatementContext): CSTCircuitStatement {
        return when (ctx) {
            is CSTParser.ConditionalCircuitStatementContext -> visitConditionalCircuitStatement(ctx)
            is CSTParser.NonConditionalCircuitStatementContext -> visitNonConditionalCircuitStatement(ctx)
            else -> throw IllegalArgumentException("Unexpected circuit statement context $ctx")
        }
    }

    override fun visitConditionalCircuitBody(ctx: CSTParser.ConditionalCircuitBodyContext): CSTConditionalCircuitBody {
        return CSTConditionalCircuitBody(
            circuitStatements = ctx.circuitStatement().map { visitCircuitStatement(it) }
        )
    }

    override fun visitConditional(ctx: CSTParser.ConditionalContext): CSTConditional {
        return CSTConditional(
            predicate = CSTExpressionVisitor.visitExpression(ctx.expression()),
            ifBody = visitConditionalCircuitBody(ctx.ifBody!!).circuitStatements,
            elseBody = ctx.elseBody?.let { visitConditionalCircuitBody(it).circuitStatements } ?: emptyList(),
        )
    }

    override fun visitConditionalCircuitStatement(ctx: CSTParser.ConditionalCircuitStatementContext): CSTConditionalCircuitStatement {
        val conditional = visitConditional(ctx.conditional())

        return CSTConditionalCircuitStatement(
            predicate = conditional.predicate,
            ifBody = conditional.ifBody,
            elseBody = conditional.elseBody,
        )
    }

    override fun visitNonConditionalCircuitStatement(ctx: CSTParser.NonConditionalCircuitStatementContext): CSTNonConditionalCircuitStatement {
        return CSTNonConditionalCircuitStatement(
            statement = CSTCircuitExpressionVisitor.visitCircuitExpression(ctx.circuitExpression()),
        )
    }

}