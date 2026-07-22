package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.diagnostics.SourceSpan

sealed interface CircuitExpressionNode: GAPLNode

data class CircuitConnectionExpressionNode(
    override val span: SourceSpan,
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode

data class CircuitGroupExpressionNode(
    override val span: SourceSpan,
    val expressions: List<CircuitNodeExpressionNode>,
): GAPLNode

sealed interface CircuitNodeExpressionNode: GAPLNode

data class DeclaredInterfaceCircuitExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val interfaceType: InterfaceTypeNode,
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode

data class DeclaredFunctionCircuitExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val instantiation: InstantiationNode,
): CircuitNodeExpressionNode

data class DeclaredGenericFunctionCircuitExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val functionIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class AnonymousInterfaceCircuitExpressionNode(
    override val span: SourceSpan,
    val interfaceType: InterfaceTypeNode,
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode

data class AnonymousFunctionCircuitExpressionNode(
    override val span: SourceSpan,
    val instantiation: InstantiationNode,
): CircuitNodeExpressionNode

data class AnonymousGenericFunctionCircuitExpressionNode(
    override val span: SourceSpan,
    val functionIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class ReferenceCircuitExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): CircuitNodeExpressionNode

data class ProtocolAccessorCircuitExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val memberIdentifier: IdentifierNode,
): CircuitNodeExpressionNode

data class RecordInterfaceConstructorExpressionNode(
    override val span: SourceSpan,
    val statements: List<CircuitStatementNode>,
): CircuitNodeExpressionNode

data class CircuitExpressionNodeCircuitExpression(
    override val span: SourceSpan,
    val expression: CircuitExpressionNode,
): CircuitNodeExpressionNode

data class ErrorCircuitNodeExpressionNode(
    override val span: SourceSpan,
    val message: String,
): CircuitNodeExpressionNode
