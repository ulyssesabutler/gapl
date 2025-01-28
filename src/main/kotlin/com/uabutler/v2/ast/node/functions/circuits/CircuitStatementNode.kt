package com.uabutler.v2.ast.node.functions.circuits

import com.uabutler.v2.ast.node.PersistentNode
import com.uabutler.v2.ast.node.TemporaryNode
import com.uabutler.v2.ast.node.staticexpressions.StaticExpressionNode

sealed interface CircuitStatementNode: PersistentNode

data class ConditionalCircuitStatementNode(
    val predicate: StaticExpressionNode,
    val ifBody: List<CircuitStatementNode>,
    val elseBody: List<CircuitStatementNode>,
): CircuitStatementNode

data class NonConditionalCircuitStatementNode(
    val statement: CircuitExpressionNode,
): CircuitStatementNode

data class ConditionalCircuitBodyNode(val statements: List<CircuitStatementNode>): TemporaryNode