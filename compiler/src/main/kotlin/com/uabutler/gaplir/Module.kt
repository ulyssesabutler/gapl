package com.uabutler.gaplir

import com.uabutler.gaplir.node.BodyNode
import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.gaplir.node.InputNode
import com.uabutler.gaplir.node.OutputNode
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.gaplir.util.ModuleInvocation

class Module(
    val moduleInvocation: ModuleInvocation,
    val inputStructure: List<InterfaceDescription>,
    val outputStructure: List<InterfaceDescription>,
    val inputNodes: List<InputNode>,
    val outputNodes: List<OutputNode>,
    val bodyNodes: List<BodyNode>,
) {
    val nodes = inputNodes + outputNodes + bodyNodes

    override fun toString() = genToStringFromProperties(
        instance = this,
        Module::inputStructure,
        Module::outputStructure,
        Module::inputNodes,
        Module::outputNodes,
        Module::bodyNodes
    )
}