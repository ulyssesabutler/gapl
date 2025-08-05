package com.uabutler.ast.node.functions

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.PersistentNode

sealed interface FunctionExpressionNode: PersistentNode

data class FunctionExpressionInstantiationNode(
    val instantiation: InstantiationNode,
): FunctionExpressionNode

data class FunctionExpressionReferenceNode(
    val identifier: IdentifierNode,
): FunctionExpressionNode