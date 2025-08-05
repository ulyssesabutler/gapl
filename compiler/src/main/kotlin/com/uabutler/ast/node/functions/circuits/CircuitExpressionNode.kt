package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode

sealed interface CircuitExpressionNode: PersistentNode

data class CircuitConnectionExpressionNode(
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode

data class CircuitGroupExpressionNode(
    val expressions: List<CircuitNodeExpressionNode>,
): PersistentNode

sealed interface CircuitNodeExpressionNode: PersistentNode

data class CircuitNodeCreationExpressionNode(
    val identifier: IdentifierNode?,
    val interior: CircuitNodeInteriorNode,
): CircuitNodeExpressionNode

data class CircuitNodeReferenceExpressionNode(
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): CircuitNodeExpressionNode

data class CircuitNodeLiteralExpressionNode(
    val literal: StaticExpressionNode,
): CircuitNodeExpressionNode