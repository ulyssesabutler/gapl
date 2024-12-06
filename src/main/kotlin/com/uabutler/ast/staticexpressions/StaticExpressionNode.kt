package com.uabutler.ast.staticexpressions

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.IntegerLiteralNode
import com.uabutler.ast.PersistentNode

sealed interface StaticExpressionNode: PersistentNode

data object TrueStaticExpressionNode: StaticExpressionNode
data object FalseStaticExpressionNode: StaticExpressionNode
data class IntegerLiteralStaticExpressionNode(val integer: IntegerLiteralNode): StaticExpressionNode
data class IdentifierStaticExpressionNode(val identifier: IdentifierNode): StaticExpressionNode
data class AdditionNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class SubtractNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class MultiplyNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class DivideNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class EqualsNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class NotEqualsNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class LessThanNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class GreaterThanNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class LessThanEqualsNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode
data class GreaterThanEqualsNode(val lhs: StaticExpressionNode, val rhs: StaticExpressionNode): StaticExpressionNode