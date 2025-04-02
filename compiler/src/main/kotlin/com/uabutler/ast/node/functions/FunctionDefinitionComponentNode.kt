package com.uabutler.ast.node.functions

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.TemporaryNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

sealed interface FunctionTypeNode: PersistentNode

class DefaultFunctionTypeNode: FunctionTypeNode {
    override fun toString(): String = DefaultFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is DefaultFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class SequentialFunctionTypeNode: FunctionTypeNode {
    override fun toString(): String = SequentialFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is SequentialFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class CombinationalFunctionTypeNode: FunctionTypeNode {
    override fun toString(): String = CombinationalFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is CombinationalFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

sealed interface FunctionIOTypeNode: PersistentNode

class DefaultFunctionIOTypeNode: FunctionIOTypeNode {
    override fun toString(): String = DefaultFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is DefaultFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class SequentialFunctionIOTypeNode: FunctionIOTypeNode {
    override fun toString(): String = SequentialFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is SequentialFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class CombinationalFunctionIOTypeNode: FunctionIOTypeNode {
    override fun toString(): String = CombinationalFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is CombinationalFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

data class FunctionIONode(
    val identifier: IdentifierNode,
    val ioType: FunctionIOTypeNode,
    val interfaceType: InterfaceExpressionNode,
): PersistentNode

sealed interface FunctionIOListNode: TemporaryNode

data object EmptyFunctionIOListNode: FunctionIOListNode
data class NonEmptyFunctionIOListNode(val functionIO: List<FunctionIONode>): FunctionIOListNode