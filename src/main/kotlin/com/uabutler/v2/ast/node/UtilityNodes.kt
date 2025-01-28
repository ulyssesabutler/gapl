package com.uabutler.v2.ast.node

import com.uabutler.v2.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.v2.ast.node.staticexpressions.StaticExpressionNode

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
    val typeIdentifier: IdentifierNode,
): PersistentNode

data class GenericInterfaceDefinitionListNode(val interfaces: List<GenericInterfaceDefinitionNode>): TemporaryNode
data class GenericParameterDefinitionListNode(val parameters: List<GenericParameterDefinitionNode>): TemporaryNode

data class GenericInterfaceValueNode(
    val value: InterfaceExpressionNode,
): PersistentNode

data class GenericParameterValueNode(
    val value: StaticExpressionNode,
): PersistentNode

data class GenericInterfaceValueListNode(val interfaces: List<GenericInterfaceValueNode>): TemporaryNode
data class GenericParameterValueListNode(val parameters: List<GenericParameterValueNode>): TemporaryNode

data class VectorBoundsNode(
    val boundSpecifier: StaticExpressionNode,
): PersistentNode