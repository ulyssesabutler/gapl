package com.uabutler.ast.functions.circuits

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.staticexpressions.StaticExpressionNode

sealed interface SingleAccessOperationNode: PersistentNode

data class MemberAccessOperationNode(val memberIdentifier: IdentifierNode): SingleAccessOperationNode
data class SingleArrayAccessOperationNode(val index: StaticExpressionNode): SingleAccessOperationNode

data class MultipleAccessOperationNode(val startIndex: StaticExpressionNode, val endIndex: StaticExpressionNode): PersistentNode