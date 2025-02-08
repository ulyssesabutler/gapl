package com.uabutler.ast.node.interfaces

import com.uabutler.ast.node.*

sealed interface InterfaceExpressionNode: PersistentNode

class WireInterfaceExpressionNode: InterfaceExpressionNode {
    override fun toString(): String = WireInterfaceExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is WireInterfaceExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

data class DefinedInterfaceExpressionNode(
    val interfaceIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): InterfaceExpressionNode

data class VectorInterfaceExpressionNode(
    val vectoredInterface: InterfaceExpressionNode,
    val boundsSpecifier: VectorBoundsNode,
): InterfaceExpressionNode

data class IdentifierInterfaceExpressionNode(
    val interfaceIdentifier: IdentifierNode,
): InterfaceExpressionNode
