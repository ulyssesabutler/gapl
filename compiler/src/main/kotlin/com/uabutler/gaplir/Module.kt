package com.uabutler.gaplir

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.gaplir.node.ModuleInputNode
import com.uabutler.gaplir.node.ModuleOutputNode
import com.uabutler.gaplir.node.Node
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.gaplir.util.ModuleInvocation

class Module(
    val moduleInvocation: ModuleInvocation,
    val inputStructure: List<InterfaceDescription>,
    val outputStructure: List<InterfaceDescription>,
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