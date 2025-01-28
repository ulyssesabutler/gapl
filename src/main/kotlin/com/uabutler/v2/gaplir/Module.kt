package com.uabutler.v2.gaplir

import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.util.ModuleInvocation

data class Module(
    val moduleInvocation: ModuleInvocation,
    val input: Map<String, InterfaceStructure>,
    val output: Map<String, InterfaceStructure>,
    val nodes: List<Node>,
)