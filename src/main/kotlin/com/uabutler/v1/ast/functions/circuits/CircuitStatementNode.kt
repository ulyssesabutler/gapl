package com.uabutler.v1.ast.functions.circuits

import com.uabutler.v1.ast.PersistentNode
import com.uabutler.v1.ast.TemporaryNode
import com.uabutler.v1.ast.staticexpressions.StaticExpressionNode

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