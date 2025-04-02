package com.uabutler.ast.visitor

import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.ast.node.functions.circuits.CircuitStatementNode
import com.uabutler.ast.node.functions.circuits.ConditionalCircuitBodyNode
import com.uabutler.ast.node.functions.circuits.ConditionalCircuitStatementNode
import com.uabutler.ast.node.functions.circuits.NonConditionalCircuitStatementNode

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
        return NonConditionalCircuitStatementNode(
            statement = CircuitExpressionVisitor.visitCircuitExpression(ctx.circuitExpression())
        )
    }

    override fun visitConditionalCircuit(ctx: GAPLParser.ConditionalCircuitContext): ConditionalCircuitStatementNode {
        return ConditionalCircuitStatementNode(
            predicate = StaticExpressionVisitor.visitStaticExpression(ctx.predicate!!),
            ifBody = visitConditionalCircuitBody(ctx.ifBody!!).statements,
            elseBody = ctx.elseBody?.let { visitConditionalCircuitBody(it).statements } ?: emptyList(),
        )
    }

    override fun visitConditionalCircuitBody(ctx: GAPLParser.ConditionalCircuitBodyContext): ConditionalCircuitBodyNode {
        return ConditionalCircuitBodyNode(
            statements = ctx.circuitStatement().map { visitCircuitStatement(it) }
        )
    }

}