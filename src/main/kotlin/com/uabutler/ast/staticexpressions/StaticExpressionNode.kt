package com.uabutler.ast.staticexpressions

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.IntegerLiteralNode
import com.uabutler.ast.PersistentNode

sealed interface StaticExpressionNode: PersistentNode

data object TrueStaticExpressionNode: StaticExpressionNode
data object FalseStaticExpressionNode: StaticExpressionNode
data class IntegerLiteralStaticExpressionNode(val integer: IntegerLiteralNode): StaticExpressionNode
data class IdentifierStaticExpressionNode(val identifier: IdentifierNode): StaticExpressionNode
data class AdditionStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class SubtractionStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class MultiplicationStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class DivisionStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class EqualsStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class NotEqualsStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class LessThanStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class GreaterThanStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class LessThanEqualsStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class GreaterThanEqualsStaticExpressionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode