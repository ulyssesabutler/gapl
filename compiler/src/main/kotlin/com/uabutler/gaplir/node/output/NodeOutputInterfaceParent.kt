package com.uabutler.gaplir.node.output

import com.uabutler.gaplir.node.Node

sealed class NodeOutputInterfaceParent

sealed class NodeOutputInterfaceParentInterface: NodeOutputInterfaceParent() {
    abstract val parentInterface: NodeOutputInterface
}

data class NodeOutputInterfaceParentVectorInterface(
    override val parentInterface: NodeOutputVectorInterface,
    val parentIndex: Int,
): NodeOutputInterfaceParentInterface()

data class NodeOutputInterfaceParentRecordInterface(
    override val parentInterface: NodeOutputRecordInterface,
    val parentMember: String,
): NodeOutputInterfaceParentInterface()

data class NodeOutputInterfaceParentNode(
    var parentNode: Node,
    val parentIndex: Int,
    val parentName: String,
): NodeOutputInterfaceParent()
