package com.uabutler.ast.functions

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode
import com.uabutler.ast.interfaces.InterfaceExpressionNode
import com.uabutler.ast.staticexpressions.TrueStaticExpressionNode

sealed interface FunctionTypeNode: PersistentNode

class DefaultFunctionTypeNode: FunctionTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = DefaultFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is DefaultFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class SequentialFunctionTypeNode: FunctionTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = SequentialFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is SequentialFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class CombinationalFunctionTypeNode: FunctionTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = CombinationalFunctionTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is CombinationalFunctionTypeNode
    override fun hashCode() = javaClass.hashCode()
}

sealed interface FunctionIOTypeNode: PersistentNode

class DefaultFunctionIOTypeNode: FunctionIOTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = DefaultFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is DefaultFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class SequentialFunctionIOTypeNode: FunctionIOTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = SequentialFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is SequentialFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class CombinationalFunctionIOTypeNode: FunctionIOTypeNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = CombinationalFunctionIOTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is CombinationalFunctionIOTypeNode
    override fun hashCode() = javaClass.hashCode()
}

data class FunctionIONode(
    val identifier: IdentifierNode,
    val ioType: FunctionIOTypeNode,
    val interfaceType: InterfaceExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

sealed interface FunctionIOListNode: TemporaryNode

data object EmptyFunctionIOListNode: FunctionIOListNode
data class NonEmptyFunctionIOListNode(val functionIO: List<FunctionIONode>): FunctionIOListNode