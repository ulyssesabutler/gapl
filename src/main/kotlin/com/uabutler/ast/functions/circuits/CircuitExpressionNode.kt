package com.uabutler.ast.functions.circuits

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.interfaces.InterfaceExpressionNode
import javax.lang.model.type.DeclaredType

sealed interface CircuitExpressionNode: PersistentNode

data class CircuitConnectionExpressionNode(
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode

data class CircuitGroupExpressionNode(
    val expressions: List<CircuitNodeExpressionNode>,
): PersistentNode

sealed interface CircuitNodeExpressionNode: PersistentNode

data class IdentifierCircuitExpressionNode(
    val identifier: IdentifierNode,
): CircuitNodeExpressionNode

data class AnonymousNodeCircuitExpressionNode(
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode

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