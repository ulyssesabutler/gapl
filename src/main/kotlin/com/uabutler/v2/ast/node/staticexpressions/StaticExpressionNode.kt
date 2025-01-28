package com.uabutler.v2.ast.node.staticexpressions

import com.uabutler.v2.ast.node.IdentifierNode
import com.uabutler.v2.ast.node.IntegerLiteralNode
import com.uabutler.v2.ast.node.PersistentNode

sealed interface StaticExpressionNode: PersistentNode

class TrueStaticExpressionNode: StaticExpressionNode {
    override fun toString(): String = TrueStaticExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is TrueStaticExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

class FalseStaticExpressionNode: StaticExpressionNode {
    override fun toString(): String = FalseStaticExpressionNode::class.java.simpleName
    override fun equals(other: Any?) = other is FalseStaticExpressionNode
    override fun hashCode() = javaClass.hashCode()
}

data class IntegerLiteralStaticExpressionNode(
    val integer: IntegerLiteralNode,
): StaticExpressionNode

data class IdentifierStaticExpressionNode(
    val identifier: IdentifierNode,
): StaticExpressionNode

data class AdditionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class SubtractionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class MultiplicationStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class DivisionStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class EqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class NotEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class LessThanStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class GreaterThanStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class LessThanEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class GreaterThanEqualsStaticExpressionNode(
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode