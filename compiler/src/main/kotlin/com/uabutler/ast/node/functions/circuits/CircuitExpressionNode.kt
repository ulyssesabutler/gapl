package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

sealed interface CircuitExpressionNode: PersistentNode

data class CircuitConnectionExpressionNode(
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode

data class CircuitGroupExpressionNode(
    val expressions: List<CircuitNodeExpressionNode>,
): PersistentNode

sealed interface CircuitNodeExpressionNode: PersistentNode

data class DeclaredInterfaceCircuitExpressionNode(
    val identifier: IdentifierNode,
    val interfaceType: InterfaceTypeNode,
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode

data class DeclaredFunctionCircuitExpressionNode(
    val identifier: IdentifierNode,
    val instantiation: InstantiationNode,
): CircuitNodeExpressionNode

data class DeclaredGenericFunctionCircuitExpressionNode(
    val identifier: IdentifierNode,
    val functionIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class AnonymousFunctionCircuitExpressionNode(
    val instantiation: InstantiationNode,
): CircuitNodeExpressionNode

data class AnonymousGenericFunctionCircuitExpressionNode(
    val functionIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class ReferenceCircuitExpressionNode(
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): CircuitNodeExpressionNode

data class ProtocolAccessorCircuitExpressionNode(
    val identifier: IdentifierNode,
    val memberIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class RecordInterfaceConstructorExpressionNode(
    val statements: List<CircuitStatementNode>,
): CircuitNodeExpressionNode

data class CircuitExpressionNodeCircuitExpression(
    val expression: CircuitExpressionNode,
): CircuitNodeExpressionNode