package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.diagnostics.SourceSpan

sealed interface SingleAccessOperationNode: GAPLNode

data class MemberAccessOperationNode(
    override val span: SourceSpan,
    val memberIdentifier: IdentifierNode,
): SingleAccessOperationNode

data class SingleArrayAccessOperationNode(
    override val span: SourceSpan,
    val index: StaticExpressionNode,
): SingleAccessOperationNode

data class MultipleAccessOperationNode(
    override val span: SourceSpan,
    val startIndex: StaticExpressionNode,
    val endIndex: StaticExpressionNode,
): GAPLNode
