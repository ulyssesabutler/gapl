package com.uabutler.ast.node.functions

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.TemporaryNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

data class AbstractFunctionIONode(
    val interfaceType: InterfaceTypeNode,
    val interfaceExpression: InterfaceExpressionNode,
): PersistentNode

data class FunctionIONode(
    val identifier: IdentifierNode,
    val interfaceType: InterfaceTypeNode,
    val interfaceExpression: InterfaceExpressionNode,
): PersistentNode

sealed interface AbstractFunctionIOListNode: TemporaryNode

data object EmptyAbstractFunctionIOListNode: AbstractFunctionIOListNode
data class NonEmptyAbstractFunctionIOListNode(val functionIO: List<AbstractFunctionIONode>): AbstractFunctionIOListNode

sealed interface FunctionIOListNode: TemporaryNode

data object EmptyFunctionIOListNode: FunctionIOListNode
data class NonEmptyFunctionIOListNode(val functionIO: List<FunctionIONode>): FunctionIOListNode