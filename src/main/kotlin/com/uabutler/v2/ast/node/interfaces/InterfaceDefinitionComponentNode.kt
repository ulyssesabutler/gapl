package com.uabutler.v2.ast.node.interfaces

import com.uabutler.v2.ast.node.IdentifierNode
import com.uabutler.v2.ast.node.PersistentNode
import com.uabutler.v2.ast.node.TemporaryNode

data class RecordInterfacePortNode(
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): PersistentNode

data class RecordInterfaceInheritListNode(val inherits: List<DefinedInterfaceExpressionNode>): TemporaryNode
data class RecordInterfacePortListNode(val ports: List<RecordInterfacePortNode>): TemporaryNode