package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

sealed interface CircuitExpressionNode: PersistentNode

data class CircuitConnectionExpressionNode(
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode

data class CircuitGroupExpressionNode(
    val expressions: List<CircuitNodeExpressionNode>,
): PersistentNode

sealed interface CircuitNodeExpressionNode: PersistentNode

data class DeclaredNodeCircuitExpressionNode(
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode

data class ReferenceCircuitExpressionNode(
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): CircuitNodeExpressionNode

data class RecordInterfaceConstructorExpressionNode(
    val statements: List<CircuitStatementNode>,
): CircuitNodeExpressionNode

data class CircuitExpressionNodeCircuitExpression(
    val expression: CircuitExpressionNode,
): CircuitNodeExpressionNode