package com.uabutler.ast

import com.uabutler.ast.interfaces.InterfaceExpressionNode
import com.uabutler.ast.staticexpressions.StaticExpressionNode

object EmptyNode: TemporaryNode

data class InstantiationNode(
    val definitionIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class GenericInterfaceDefinitionNode(
    val identifier: IdentifierNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class GenericParameterDefinitionNode(
    val identifier: IdentifierNode,
    val typeIdentifier: IdentifierNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class GenericInterfaceDefinitionListNode(val interfaces: List<GenericInterfaceDefinitionNode>): TemporaryNode
data class GenericParameterDefinitionListNode(val parameters: List<GenericParameterDefinitionNode>): TemporaryNode

data class GenericInterfaceValueNode(
    val value: InterfaceExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class GenericParameterValueNode(
    val value: StaticExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class GenericInterfaceValueListNode(val interfaces: List<GenericInterfaceValueNode>): TemporaryNode
data class GenericParameterValueListNode(val parameters: List<GenericParameterValueNode>): TemporaryNode

data class VectorBoundsNode(
    val boundSpecifier: StaticExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}