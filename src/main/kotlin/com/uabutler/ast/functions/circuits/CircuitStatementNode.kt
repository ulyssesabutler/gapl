package com.uabutler.ast.functions.circuits

import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode
import com.uabutler.ast.staticexpressions.StaticExpressionNode

sealed interface CircuitStatementNode: PersistentNode

data class ConditionalCircuitStatementNode(
    val predicate: StaticExpressionNode,
    val ifBody: List<CircuitStatementNode>,
    val elseBody: List<CircuitStatementNode>,
): CircuitStatementNode {
    override var parent: PersistentNode? = null
}

data class NonConditionalCircuitStatementNode(
    val statement: CircuitExpressionNode,
): CircuitStatementNode {
    override var parent: PersistentNode? = null
}

data class ConditionalCircuitBodyNode(val statements: List<CircuitStatementNode>): TemporaryNode