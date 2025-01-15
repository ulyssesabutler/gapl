package com.uabutler.v1.ast.staticexpressions

import com.uabutler.v1.ast.IdentifierNode
import com.uabutler.v1.ast.IntegerLiteralNode
import com.uabutler.v1.ast.PersistentNode

sealed interface StaticExpressionNode: PersistentNode

class TrueStaticExpressionNode: StaticExpressionNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = TrueStaticExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is TrueStaticExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

class FalseStaticExpressionNode: StaticExpressionNode {
    override var parent: PersistentNode? = null

    override fun toString(): String = FalseStaticExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is FalseStaticExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

data class IntegerLiteralStaticExpressionNode(
    val integer: IntegerLiteralNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class IdentifierStaticExpressionNode(
    val identifier: IdentifierNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class AdditionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class SubtractionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class MultiplicationStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class DivisionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class EqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class NotEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class LessThanStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class GreaterThanStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class LessThanEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}

data class GreaterThanEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode {
    override var parent: PersistentNode? = null
}