package com.uabutler.ast.interfaces

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode

data class RecordInterfacePortNode(
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class RecordInterfaceInheritListNode(val inherits: List<DefinedInterfaceExpressionNode>): TemporaryNode
data class RecordInterfacePortListNode(val ports: List<RecordInterfacePortNode>): TemporaryNode