package com.uabutler.ast.node.interfaces

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.diagnostics.SourceSpan

data class RecordInterfacePortNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): GAPLNode
