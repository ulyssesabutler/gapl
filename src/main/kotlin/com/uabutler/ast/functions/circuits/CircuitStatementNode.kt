package com.uabutler.ast.functions.circuits

import com.uabutler.ast.PersistentNode
import com.uabutler.ast.staticexpressions.StaticExpressionNode

sealed interface CircuitStatementNode: PersistentNode

data class ConditionalCircuitStatementNode(
    val predicate: StaticExpressionNode,
    val ifBody: List<CircuitStatementNode>,
    val elseBody: List<CircuitStatementNode>,
): CircuitStatementNode

data class CircuitExpressionStatementNode(val circuitExpression: CircuitExpressionNode): CircuitStatementNode