package com.uabutler.ast.functions

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode
import com.uabutler.ast.interfaces.InterfaceExpressionNode

sealed interface FunctionTypeNode: PersistentNode

data object DefaultFunctionTypeNode: FunctionTypeNode
data object SequentialFunctionTypeNode: FunctionTypeNode
data object CombinationalFunctionTypeNode: FunctionTypeNode

sealed interface FunctionIOTypeNode: PersistentNode

data object DefaultFunctionIOTypeNode: FunctionIOTypeNode
data object SequentialFunctionIOTypeNode: FunctionIOTypeNode
data object CombinationalFunctionIOTypeNode: FunctionIOTypeNode

data class FunctionIONode(
    val identifier: IdentifierNode,
    val ioType: FunctionIOTypeNode,
    val interfaceType: InterfaceExpressionNode,
): PersistentNode

sealed interface FunctionIOListNode: TemporaryNode

data object EmptyFunctionIOListNode: FunctionIOListNode
data class NonEmptyFunctionIOListNode(val functionIO: List<FunctionIONode>): FunctionIOListNode