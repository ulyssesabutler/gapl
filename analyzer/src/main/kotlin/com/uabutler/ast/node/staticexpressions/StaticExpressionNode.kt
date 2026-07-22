package com.uabutler.ast.node.staticexpressions

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.IntegerLiteralNode
import com.uabutler.diagnostics.SourceSpan

sealed interface StaticExpressionNode: GAPLNode

data class TrueStaticExpressionNode(override val span: SourceSpan): StaticExpressionNode

data class FalseStaticExpressionNode(override val span: SourceSpan): StaticExpressionNode

data class IntegerLiteralStaticExpressionNode(
    override val span: SourceSpan,
    val integer: IntegerLiteralNode,
): StaticExpressionNode

data class IdentifierStaticExpressionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
): StaticExpressionNode

data class AdditionStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class SubtractionStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class MultiplicationStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class DivisionStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class RemainderStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class EqualsStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class NotEqualsStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class LessThanStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class GreaterThanStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class LessThanEqualsStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class GreaterThanEqualsStaticExpressionNode(
    override val span: SourceSpan,
    val lhs: StaticExpressionNode,
    val rhs: StaticExpressionNode,
): StaticExpressionNode

data class ErrorStaticExpressionNode(
    override val span: SourceSpan,
    val message: String,
): StaticExpressionNode
