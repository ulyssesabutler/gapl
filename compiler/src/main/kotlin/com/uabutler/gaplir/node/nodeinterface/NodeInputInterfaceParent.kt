package com.uabutler.gaplir.node.nodeinterface

import com.uabutler.gaplir.node.Node

sealed class NodeInputInterfaceParent

sealed class NodeInputInterfaceParentInterface: NodeInputInterfaceParent() {
    abstract val parentInterface: NodeInputInterface
}

data class NodeInputInterfaceParentVectorInterface(
    override val parentInterface: NodeInputVectorInterface,
    val parentIndex: Int,
): NodeInputInterfaceParentInterface()

data class NodeInputInterfaceParentRecordInterface(
    override val parentInterface: NodeInputRecordInterface,
    val parentMember: String,
): NodeInputInterfaceParentInterface()

data class NodeInputInterfaceParentNode(
    var parentNode: Node,
    val parentIndex: Int,
    val parentName: String,
): NodeInputInterfaceParent()