package com.uabutler.ast.node

import com.uabutler.ast.node.functions.AbstractFunctionIONode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.util.Protocol

data class InstantiationNode(
    override val span: SourceSpan,
    val definitionIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
    // Set when the call is annotated (`f()@ready_valid`): the call is an island, coercing the callee
    // into the caller's protocol.
    val coercion: ProtocolAnnotationNode? = null,
): GAPLNode

data class ProtocolAnnotationNode(
    override val span: SourceSpan,
    val protocol: Protocol,
): GAPLNode

data class GenericInterfaceDefinitionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
): GAPLNode

data class GenericParameterDefinitionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val type: GenericParameterTypeNode,
): GAPLNode

data class GenericInterfaceValueNode(
    override val span: SourceSpan,
    val value: InterfaceExpressionNode,
): GAPLNode

sealed interface GenericParameterValueNode: GAPLNode

data class StaticExpressionGenericParameterValueNode(
    override val span: SourceSpan,
    val value: StaticExpressionNode,
): GenericParameterValueNode

data class FunctionInstantiationGenericParameterValueNode(
    override val span: SourceSpan,
    val instantiation: InstantiationNode,
): GenericParameterValueNode

data class FunctionReferenceGenericParameterValueNode(
    override val span: SourceSpan,
    val functionIdentifier: IdentifierNode,
): GenericParameterValueNode

data class ErrorGenericParameterValueNode(
    override val span: SourceSpan,
    val message: String,
): GenericParameterValueNode

sealed interface GenericParameterTypeNode: GAPLNode

data class IdentifierGenericParameterTypeNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
): GenericParameterTypeNode

data class FunctionGenericParameterTypeNode(
    override val span: SourceSpan,
    val inputFunctionIO: List<AbstractFunctionIONode>,
    val outputFunctionIO: List<AbstractFunctionIONode>,
): GenericParameterTypeNode

data class VectorBoundsNode(
    override val span: SourceSpan,
    val boundSpecifier: StaticExpressionNode,
): GAPLNode
