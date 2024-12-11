package com.uabutler.ast.interfaces

import com.uabutler.ast.*

sealed interface InterfaceExpressionNode: PersistentNode

class WireInterfaceExpressionNode: InterfaceExpressionNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = WireInterfaceExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is WireInterfaceExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

data class DefinedInterfaceExpressionNode(
    val interfaceIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): InterfaceExpressionNode {
    override var parent: PersistentNode? = null
}

data class VectorInterfaceExpressionNode(
    val vectoredInterface: InterfaceExpressionNode,
    val boundsSpecifier: VectorBoundsNode,
): InterfaceExpressionNode {
    override var parent: PersistentNode? = null
}

data class IdentifierInterfaceExpressionNode(
    val interfaceIdentifier: IdentifierNode,
): InterfaceExpressionNode {
    override var parent: PersistentNode? = null
}
