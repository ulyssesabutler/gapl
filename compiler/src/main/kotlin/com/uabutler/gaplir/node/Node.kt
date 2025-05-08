package com.uabutler.gaplir.node

import com.uabutler.util.Named
import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.builder.util.PredefinedFunction
import com.uabutler.gaplir.node.input.NodeInputInterface
import com.uabutler.gaplir.node.input.NodeInputInterfaceParentNode
import com.uabutler.gaplir.node.output.NodeOutputInterface
import com.uabutler.gaplir.node.output.NodeOutputInterfaceParentNode
import com.uabutler.gaplir.util.ModuleInvocation

// TODO: Custom toStrings instead of data classes
sealed class Node(
    inputInterfaceStructures: List<Named<InterfaceStructure>>,
    outputInterfaceStructures: List<Named<InterfaceStructure>>,
) {
    // TODO: From structures
    val inputs: List<Named<NodeInputInterface>> = inputInterfaceStructures.mapIndexed { index, named ->
        val parent = NodeInputInterfaceParentNode(this, index, named.name)
        Named(named.name, NodeInputInterface.fromStructure(parent, named.item))
    }
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
    val invokedModuleName: String,

    val moduleInvocation: ModuleInvocation,

    val functionInputInterfaces: List<Named<InterfaceStructure>>,
    val functionOutputInterfaces: List<Named<InterfaceStructure>>,
): Node(
    inputInterfaceStructures = functionInputInterfaces.map { Named("${invokedModuleName}_${it.name}", it.item) },
    outputInterfaceStructures = functionOutputInterfaces.map { Named("${invokedModuleName}_${it.name}", it.item) },
) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInvocationNode::moduleInvocation,
        ModuleInvocationNode::inputs,
        ModuleInvocationNode::outputs,
        ModuleInvocationNode::functionInputInterfaces,
        ModuleInvocationNode::functionOutputInterfaces,
    )
}

class PredefinedFunctionInvocationNode(
    val invocationName: String,
    val predefinedFunction: PredefinedFunction,
): Node(
    inputInterfaceStructures = predefinedFunction.inputs.map { Named("${invocationName}_${it.key}", it.value) },
    outputInterfaceStructures = predefinedFunction.outputs.map { Named("${invocationName}_${it.key}", it.value) },
)

// TODO: Create nodes for built-in functions, register, priority queues, etc.