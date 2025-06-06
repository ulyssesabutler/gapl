package com.uabutler.gaplir.node.nodeinterface

data class NodeTopLevelInputInterface(
    val inputInterface: NodeInputInterface,
    val protocol: Map<String, NodeInterface>,
)
