package com.uabutler.v2.gaplir

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.util.ModuleInvocation

class Module(
    val moduleInvocation: ModuleInvocation,
    val input: Map<String, InterfaceStructure>,
    val output: Map<String, InterfaceStructure>,
    val nodes: List<Node>,
) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        Module::input,
        Module::output,
        Module::nodes
    )
}