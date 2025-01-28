package com.uabutler.v2.gaplir.node

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.builder.util.AnonymousIdentifierGenerator
import com.uabutler.v2.gaplir.node.input.NodeInputInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputInterface
import com.uabutler.v2.gaplir.util.ModuleInvocation

// TODO: Custom toStrings instead of data classes
sealed class Node(
    // Default to an anonymous node
    open val name: String = AnonymousIdentifierGenerator.genIdentifier(),

    inputInterfaceStructures: List<InterfaceStructure>,
    outputInterfaceStructures: List<InterfaceStructure>,
) {
    val inputs: List<NodeInputInterface> = NodeInputInterface.fromStructures(this, inputInterfaceStructures)
    val outputs: List<NodeOutputInterface> = NodeOutputInterface.fromStructures(this, outputInterfaceStructures)
}

class ModuleInputNode(
    override val name: String,
    val inputInterfaceStructure: InterfaceStructure,
    // A bit counter-intuitively, the input node only has output wires
): Node(name, emptyList(), listOf(inputInterfaceStructure)) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInputNode::name,
        ModuleInputNode::outputs,
    )
}

class ModuleOutputNode(
    override val name: String,
    val outputInterfaceStructure: InterfaceStructure,
    // A bit counter-intuitively, the output node only has input wires
): Node(name, listOf(outputInterfaceStructure), emptyList()) {
    override fun toString(): String = genToStringFromProperties(
        instance = this,
        ModuleOutputNode::name,
        ModuleOutputNode::inputs,
    )
}

class PassThroughNode(
    override val name: String = AnonymousIdentifierGenerator.genIdentifier(),
    val interfaceStructures: List<InterfaceStructure>,
): Node(name, interfaceStructures, interfaceStructures) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        PassThroughNode::name,
        PassThroughNode::inputs,
        PassThroughNode::outputs,
    )
}

class ModuleInvocationNode(
    val invocationName: String = AnonymousIdentifierGenerator.genIdentifier(),

    val moduleInvocation: ModuleInvocation,

    val functionInputInterfaces: List<Pair<String, InterfaceStructure>>,
    val functionOutputInterfaces: List<Pair<String, InterfaceStructure>>,
): Node(invocationName, functionInputInterfaces.map { it.second }, functionOutputInterfaces.map { it.second }) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInvocationNode::invocationName,
        ModuleInvocationNode::inputs,
        ModuleInvocationNode::outputs,
    )
}

// TODO: Create nodes for built-in functions, register, priority queues, etc.