package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode

sealed interface SingleAccessOperationNode: PersistentNode

data class MemberAccessOperationNode(
    val memberIdentifier: IdentifierNode,
): SingleAccessOperationNode

data class SingleArrayAccessOperationNode(
    val index: StaticExpressionNode,
): SingleAccessOperationNode

data class MultipleAccessOperationNode(
    val startIndex: StaticExpressionNode,
    val endIndex: StaticExpressionNode,
): PersistentNode