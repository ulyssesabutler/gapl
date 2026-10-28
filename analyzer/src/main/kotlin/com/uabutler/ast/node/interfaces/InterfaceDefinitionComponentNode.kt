package com.uabutler.ast.node.interfaces

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.functions.circuits.MultipleAccessOperationNode
import com.uabutler.ast.node.functions.circuits.SingleAccessOperationNode
import com.uabutler.diagnostics.SourceSpan

data class RecordInterfacePortNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): GAPLNode

/**
 * One field of a view: a path into the view's base, in the same shape a circuit reference uses
 * (`singleAccesses` in source order, then at most one trailing slice).
 */
data class ViewFieldNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): GAPLNode
