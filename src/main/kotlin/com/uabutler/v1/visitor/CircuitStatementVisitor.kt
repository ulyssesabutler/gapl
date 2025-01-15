package com.uabutler.v1.visitor

import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.v1.ast.functions.circuits.CircuitStatementNode
import com.uabutler.v1.ast.functions.circuits.ConditionalCircuitBodyNode
import com.uabutler.v1.ast.functions.circuits.ConditionalCircuitStatementNode
import com.uabutler.v1.ast.functions.circuits.NonConditionalCircuitStatementNode

object CircuitStatementVisitor: GAPLVisitor() {

    fun visitCircuitStatement(ctx: GAPLParser.CircuitStatementContext): CircuitStatementNode {
        return when (ctx) {
            is GAPLParser.ConditionalCircuitStatementContext -> visitConditionalCircuitStatement(ctx)
            is GAPLParser.NonConditionalCircuitStatementContext -> visitNonConditionalCircuitStatement(ctx)
            else -> throw Exception("Unrecognized circuit statement")
        }
    }

    override fun visitConditionalCircuitStatement(ctx: GAPLParser.ConditionalCircuitStatementContext): ConditionalCircuitStatementNode {
        return visitConditionalCircuit(ctx.conditionalCircuit())
    }

    override fun visitNonConditionalCircuitStatement(ctx: GAPLParser.NonConditionalCircuitStatementContext): NonConditionalCircuitStatementNode {
        val current = NonConditionalCircuitStatementNode(
            statement = CircuitExpressionVisitor.visitCircuitExpression(ctx.circuitExpression())
        )
        current.statement.parent = current
        return current
    }

    override fun visitConditionalCircuit(ctx: GAPLParser.ConditionalCircuitContext): ConditionalCircuitStatementNode {
        val current = ConditionalCircuitStatementNode(
            predicate = StaticExpressionVisitor.visitStaticExpression(ctx.predicate!!),
            ifBody = visitConditionalCircuitBody(ctx.ifBody!!).statements,
            elseBody = ctx.elseBody?.let { visitConditionalCircuitBody(it).statements } ?: emptyList(),
        )
        current.predicate.parent = current
        current.ifBody.forEach { it.parent = current }
        current.elseBody.forEach { it.parent = current }
        return current
    }

    override fun visitConditionalCircuitBody(ctx: GAPLParser.ConditionalCircuitBodyContext): ConditionalCircuitBodyNode {
        return ConditionalCircuitBodyNode(
            statements = ctx.circuitStatement().map { visitCircuitStatement(it) }
        )
    }

}