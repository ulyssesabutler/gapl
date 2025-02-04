package com.uabutler.v2.gaplir.node

import com.uabutler.util.Named
import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.node.input.NodeInputInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputInterfaceParentNode
import com.uabutler.v2.gaplir.util.ModuleInvocation

// TODO: Custom toStrings instead of data classes
sealed class Node(
    inputInterfaceStructures: List<Named<InterfaceStructure>>,
    outputInterfaceStructures: List<Named<InterfaceStructure>>,
) {
    // TODO: From structures
    val inputs: List<Named<NodeInputInterface>> = inputInterfaceStructures.map { Named(it.name, NodeInputInterface.fromStructure(it.item)) }
    val outputs: List<Named<NodeOutputInterface>> = outputInterfaceStructures.mapIndexed { index, named ->
        val parent = NodeOutputInterfaceParentNode(this, index, named.name)
        val nodeOutputInterface = NodeOutputInterface.fromStructure(parent, named.item)
        Named(named.name, nodeOutputInterface)
    }
}

class ModuleInputNode(
    val name: String,
    val inputInterfaceStructure: InterfaceStructure,
    // A bit counter-intuitively, the input node only has output wires
): Node(emptyList(), listOf(Named(name, inputInterfaceStructure))) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInputNode::name,
        ModuleInputNode::outputs,
    )
}

class ModuleOutputNode(
    val name: String,
    val outputInterfaceStructure: InterfaceStructure,
    // A bit counter-intuitively, the output node only has input wires
): Node(listOf(Named(name, outputInterfaceStructure)), emptyList()) {
    override fun toString(): String = genToStringFromProperties(
        instance = this,
        ModuleOutputNode::name,
        ModuleOutputNode::inputs,
    )
}

class PassThroughNode(
    val interfaceStructures: List<Named<InterfaceStructure>>,
): Node(interfaceStructures, interfaceStructures) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        PassThroughNode::inputs,
        PassThroughNode::outputs,
    )
}

class ModuleInvocationNode(
    val moduleInvocation: ModuleInvocation,

    val functionInputInterfaces: List<Named<InterfaceStructure>>,
    val functionOutputInterfaces: List<Named<InterfaceStructure>>,
): Node(functionInputInterfaces, functionOutputInterfaces) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInvocationNode::moduleInvocation,
        ModuleInvocationNode::inputs,
        ModuleInvocationNode::outputs,
    )
}

// TODO: Create nodes for built-in functions, register, priority queues, etc.