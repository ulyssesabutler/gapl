package com.uabutler.v2.gaplir

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.v2.gaplir.node.ModuleInputNode
import com.uabutler.v2.gaplir.node.ModuleOutputNode
import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.util.ModuleInvocation

class Module(
    val moduleInvocation: ModuleInvocation,
    val inputStructure: Map<String, InterfaceStructure>,
    val outputStructure: Map<String, InterfaceStructure>,
    val inputNodes: List<ModuleInputNode>,
    val outputNodes: List<ModuleOutputNode>,
    val nodes: List<Node>,
) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        Module::inputStructure,
        Module::outputStructure,
        Module::inputNodes,
        Module::outputNodes,
        Module::nodes
    )
}