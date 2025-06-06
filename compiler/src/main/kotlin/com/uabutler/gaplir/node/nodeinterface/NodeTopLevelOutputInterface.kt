package com.uabutler.gaplir.node.nodeinterface

data class NodeTopLevelOutputInterface(
    val outputInterface: NodeOutputInterface,
    val protocol: Map<String, NodeInterface>,
)