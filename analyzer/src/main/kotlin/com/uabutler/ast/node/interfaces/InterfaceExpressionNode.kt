package com.uabutler.ast.node.interfaces

import com.uabutler.ast.node.*
import com.uabutler.diagnostics.SourceSpan

sealed interface InterfaceExpressionNode: GAPLNode

data class WireInterfaceExpressionNode(override val span: SourceSpan): InterfaceExpressionNode

data class DefinedInterfaceExpressionNode(
    override val span: SourceSpan,
    val interfaceIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): InterfaceExpressionNode

data class VectorInterfaceExpressionNode(
    override val span: SourceSpan,
    val vectoredInterface: InterfaceExpressionNode,
    val boundsSpecifier: VectorBoundsNode,
): InterfaceExpressionNode

data class IdentifierInterfaceExpressionNode(
    override val span: SourceSpan,
    val interfaceIdentifier: IdentifierNode,
): InterfaceExpressionNode

data class ErrorInterfaceExpressionNode(
    override val span: SourceSpan,
    val message: String,
): InterfaceExpressionNode
