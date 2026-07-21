package com.uabutler.resolver.scope.functions.circuits

import com.uabutler.ast.node.functions.circuits.CircuitStatementNode
import com.uabutler.ast.node.functions.circuits.ConditionalCircuitStatementNode
import com.uabutler.ast.node.functions.circuits.NonConditionalCircuitStatementNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.DeclaredSymbol
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.functions.FunctionDefinitionScope.Companion.declarationsFromCircuitExpression
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope

class CircuitStatementScope(
    parentScope: Scope,
    val circuitStatement: CSTParser.CircuitStatementContext,
): Scope by parentScope {

    fun ast(): CircuitStatementNode {
        val span = SourceSpan.of(circuitStatement)

        return when (circuitStatement) {
            is CSTParser.ConditionalCircuitStatementContext -> {
                val conditional = circuitStatement.conditional()!!
                val predicate = StaticExpressionScope(this, conditional.predicate!!).ast()
                val ifBody = CircuitStatementConditionalBodyScope(this, conditional.ifBody).ast()
                val elseBody = conditional.elseBody?.let { CircuitStatementConditionalBodyScope(this, it).ast() } ?: emptyList()

                ConditionalCircuitStatementNode(span, predicate, ifBody, elseBody)
            }
            is CSTParser.NonConditionalCircuitStatementContext -> {
                val statement = CircuitExpressionScope(this, circuitStatement.circuitExpression()!!).ast()
                NonConditionalCircuitStatementNode(span, statement)
            }
            else -> throw IllegalStateException("Unexpected circuit statement context $circuitStatement")
        }
    }

}

class CircuitStatementConditionalBodyScope(
    override val parentScope: Scope,
    val body: CSTParser.ConditionalCircuitBodyContext?,
): Scope {

    private val declaredNodes = body?.circuitStatement()
        ?.filterIsInstance<CSTParser.NonConditionalCircuitStatementContext>()
        ?.map { it.circuitExpression()!! }
        ?.flatMap { declarationsFromCircuitExpression(it) }
        ?: emptyList()

    private val nodes = declaredNodes.associateBy { it.declaredIdentifier!!.text!! }

    override fun resolveLocal(name: String): ResolvedSymbol? {
        return nodes[name]?.let { ResolvedSymbol.CircuitNode(it) }
    }

    override fun symbols(): List<DeclaredSymbol> {
        return declaredNodes.map { DeclaredSymbol(it.declaredIdentifier!!.text!!, it.declaredIdentifier!!) }
    }

    fun ast(): List<CircuitStatementNode> {
        return body?.circuitStatement()?.map { CircuitStatementScope(this, it).ast() } ?: emptyList()
    }

}
