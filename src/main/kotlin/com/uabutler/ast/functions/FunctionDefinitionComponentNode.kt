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

data object DefaultFunctionIOTypeNode: FunctionTypeNode
data object SequentialFunctionIOTypeNode: FunctionTypeNode
data object CombinationalFunctionIOTypeNode: FunctionTypeNode

data class FunctionIONode(
    val identifier: IdentifierNode,
    val ioType: FunctionIOTypeNode,
    val interfaceType: InterfaceExpressionNode,
): PersistentNode

data class FunctionIOListNode(val functionIO: List<FunctionIONode>): TemporaryNode