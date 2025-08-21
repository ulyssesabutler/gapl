package com.uabutler.resolver.scope.functions.circuits

import com.uabutler.ast.node.functions.circuits.CircuitStatementNode
import com.uabutler.ast.node.functions.circuits.ConditionalCircuitStatementNode
import com.uabutler.ast.node.functions.circuits.NonConditionalCircuitStatementNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.functions.CSTCircuitStatement
import com.uabutler.cst.node.functions.CSTConditionalCircuitStatement
import com.uabutler.cst.node.functions.CSTNonConditionalCircuitStatement
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.functions.FunctionDefinitionScope.Companion.declarationsFromCircuitExpression
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope

class CircuitStatementScope(
    parentScope: Scope,
    val circuitStatement: CSTCircuitStatement,
): Scope by parentScope {

    fun ast(): CircuitStatementNode {
        return when (circuitStatement) {
            is CSTConditionalCircuitStatement -> {
                val predicate = StaticExpressionScope(this, circuitStatement.predicate).ast()
                val ifBody = CircuitStatementConditionalBodyScope(this, circuitStatement.ifBody).ast()
                val elseBody = CircuitStatementConditionalBodyScope(this, circuitStatement.elseBody).ast()
                ConditionalCircuitStatementNode(predicate, ifBody, elseBody)
            }
            is CSTNonConditionalCircuitStatement -> {
                val statement = CircuitExpressionScope(this, circuitStatement.statement).ast()
                NonConditionalCircuitStatementNode(statement)
            }
        }
    }

}

class CircuitStatementConditionalBodyScope(
    override val parentScope: Scope,
    val body: List<CSTCircuitStatement>,
): Scope {

    private val declaredNodes = body.filterIsInstance<CSTNonConditionalCircuitStatement>()
        .map { it.statement }
        .flatMap { declarationsFromCircuitExpression(it) }
    private val nodes = declaredNodes.associateBy { it.declaredIdentifier }

    val localSymbolTable = nodes

    override fun resolveLocal(name: String): CSTPersistent? {
        return localSymbolTable[name]
    }

    override fun symbols(): List<String> {
        return buildList {
            declaredNodes.mapTo(this) { it.declaredIdentifier }
        }
    }

    fun ast(): List<CircuitStatementNode> {
        return body.map { CircuitStatementScope(this, it).ast() }
    }

}