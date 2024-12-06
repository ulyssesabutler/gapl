package com.uabutler.ast.interfaces

import com.uabutler.ast.*

sealed interface InterfaceExpressionNode: PersistentNode

data object WireInterfaceExpressionNode: InterfaceExpressionNode

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
