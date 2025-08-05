package com.uabutler.ast.node

import com.uabutler.ast.node.functions.AbstractFunctionIONode
import com.uabutler.ast.node.functions.FunctionExpressionNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode

object EmptyNode: TemporaryNode

data class InstantiationNode(
    val definitionIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): PersistentNode

data class GenericInterfaceDefinitionNode(
    val identifier: IdentifierNode,
): PersistentNode

data class GenericParameterDefinitionNode(
    val identifier: IdentifierNode,
    val type: GenericParameterTypeNode,
): PersistentNode

data class GenericInterfaceDefinitionListNode(val interfaces: List<GenericInterfaceDefinitionNode>): TemporaryNode
data class GenericParameterDefinitionListNode(val parameters: List<GenericParameterDefinitionNode>): TemporaryNode

data class GenericInterfaceValueNode(
    val value: InterfaceExpressionNode,
): PersistentNode

sealed interface GenericParameterValueNode: PersistentNode

data class StaticExpressionGenericParameterValueNode(
    val value: StaticExpressionNode,
): GenericParameterValueNode

data class FunctionExpressionParameterValueNode(
    val functionExpression: FunctionExpressionNode,
): GenericParameterValueNode

data class GenericInterfaceValueListNode(val interfaces: List<GenericInterfaceValueNode>): TemporaryNode
data class GenericParameterValueListNode(val parameters: List<GenericParameterValueNode>): TemporaryNode

sealed interface GenericParameterTypeNode: PersistentNode

data class IdentifierGenericParameterTypeNode(
    val identifier: IdentifierNode,
): GenericParameterTypeNode

data class FunctionGenericParameterTypeNode(
    val inputFunctionIO: List<AbstractFunctionIONode>,
    val outputFunctionIO: List<AbstractFunctionIONode>,
): GenericParameterTypeNode

data class VectorBoundsNode(
    val boundSpecifier: StaticExpressionNode,
): PersistentNode

sealed interface TransformerModeNode: PersistentNode

data object InTransformerModeNode: TransformerModeNode
data object OutTransformerModeNode: TransformerModeNode
data object InOutTransformerModeNode: TransformerModeNode
