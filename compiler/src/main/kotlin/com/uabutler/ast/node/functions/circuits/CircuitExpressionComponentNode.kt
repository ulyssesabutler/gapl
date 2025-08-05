package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.TransformerModeNode
import com.uabutler.ast.node.functions.FunctionExpressionNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode

sealed interface SingleAccessOperationNode: PersistentNode

data class MemberAccessOperationNode(
    val memberIdentifier: IdentifierNode,
): SingleAccessOperationNode

data class SingleArrayAccessOperationNode(
    val index: StaticExpressionNode,
): SingleAccessOperationNode

data class MultipleAccessOperationNode(
    val startIndex: StaticExpressionNode,
    val endIndex: StaticExpressionNode,
): PersistentNode

sealed interface CircuitNodeInteriorNode: PersistentNode

data class CircuitNodeFunctionInteriorNode(
    val function: FunctionExpressionNode,
): CircuitNodeInteriorNode

data class CircuitNodeInterfaceInteriorNode(
    val interfaceExpression: InterfaceExpressionNode,
): CircuitNodeInteriorNode

data class CircuitNodeInterfaceTransformerInteriorNode(
    val interfaceTransformer: CircuitNodeInterfaceTransformerNode,
    val interfaceExpression: InterfaceExpressionNode,
    val mode: TransformerModeNode,
): CircuitNodeInteriorNode

data class CircuitNodeRecordTransformerExpressionNode(
    val port: IdentifierNode,
    val expression: CircuitExpressionNode,
): PersistentNode

data class CircuitNodeListTransformerExpressionNode(
    val index: StaticExpressionNode,
    val expression: CircuitExpressionNode,
): PersistentNode

sealed interface CircuitNodeInterfaceTransformerNode: PersistentNode

data class CircuitNodeInterfaceRecordTransformerNode(
    val expressions: List<CircuitNodeRecordTransformerExpressionNode>,
): CircuitNodeInterfaceTransformerNode

data class CircuitNodeInterfaceListTransformerNode(
    val expressions: List<CircuitNodeListTransformerExpressionNode>,
): CircuitNodeInterfaceTransformerNode