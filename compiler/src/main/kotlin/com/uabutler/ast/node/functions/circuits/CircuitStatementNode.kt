package com.uabutler.ast.node.functions.circuits

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.diagnostics.SourceSpan

sealed interface CircuitStatementNode: GAPLNode

data class ConditionalCircuitStatementNode(
    override val span: SourceSpan,
    val predicate: StaticExpressionNode,
    val ifBody: List<CircuitStatementNode>,
    val elseBody: List<CircuitStatementNode>,
): CircuitStatementNode

data class NonConditionalCircuitStatementNode(
    override val span: SourceSpan,
    val statement: CircuitExpressionNode,
): CircuitStatementNode
