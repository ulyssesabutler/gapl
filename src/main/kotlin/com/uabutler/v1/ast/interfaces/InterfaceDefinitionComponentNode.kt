package com.uabutler.v1.ast.interfaces

import com.uabutler.v1.ast.IdentifierNode
import com.uabutler.v1.ast.PersistentNode
import com.uabutler.v1.ast.TemporaryNode

data class RecordInterfacePortNode(
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class RecordInterfaceInheritListNode(val inherits: List<DefinedInterfaceExpressionNode>): TemporaryNode
data class RecordInterfacePortListNode(val ports: List<RecordInterfacePortNode>): TemporaryNode