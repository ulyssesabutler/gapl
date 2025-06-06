package com.uabutler.gaplir.node

import com.uabutler.util.Named
import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.builder.util.PredefinedFunction
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterfaceParentNode
import com.uabutler.gaplir.node.nodeinterface.NodeInputWireInterface
import com.uabutler.gaplir.node.nodeinterface.NodeTopLevelInputInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentNode
import com.uabutler.gaplir.node.nodeinterface.NodeOutputWireInterface
import com.uabutler.gaplir.node.nodeinterface.NodeTopLevelOutputInterface
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.gaplir.util.ModuleInvocation
import com.uabutler.gaplir.util.StreamProtocol
import com.uabutler.util.InterfaceType


sealed class Node(
    inputInterface: List<InterfaceDescription>,
    outputInterface: List<InterfaceDescription>,
) {
    // TODO: From structures
    val inputs: List<Named<NodeTopLevelInputInterface>> = inputInterface.mapIndexed { index, description ->
        val parent = NodeInputInterfaceParentNode(this, index, description.name)

        val nodeInputInterface = NodeInputInterface.fromStructure(parent, description.interfaceStructure)

        val topLevel = when (description.interfaceType) {
            InterfaceType.SIGNAL -> NodeTopLevelInputInterface(nodeInputInterface, mapOf())
            InterfaceType.STREAM -> NodeTopLevelInputInterface(
                nodeInputInterface,
                mapOf(
                    StreamProtocol.VALID.value to NodeInputWireInterface(parent),
                    StreamProtocol.READY.value to NodeOutputWireInterface(NodeOutputInterfaceParentNode(this, index, description.name))
                )
            )
        }

        Named(description.name, topLevel)
    }
    val outputs: List<Named<NodeTopLevelOutputInterface>> = outputInterface.mapIndexed { index, description ->
        val parent = NodeOutputInterfaceParentNode(this, index, description.name)

        val nodeOutputInterface = NodeOutputInterface.fromStructure(parent, description.interfaceStructure)

        val topLevel = when (description.interfaceType) {
            InterfaceType.SIGNAL -> NodeTopLevelOutputInterface(nodeOutputInterface, mapOf())
            InterfaceType.STREAM -> NodeTopLevelOutputInterface(
                nodeOutputInterface,
                mapOf(
                    StreamProtocol.VALID.value to NodeOutputWireInterface(parent),
                    StreamProtocol.READY.value to NodeInputWireInterface(NodeInputInterfaceParentNode(this, index, description.name))
                )
            )
        }

        Named(description.name, topLevel)
    }
}

class ModuleInputNode(
    val name: String,
    val interfaceStructure: InterfaceStructure,
    val interfaceType: InterfaceType,
    // A bit counter-intuitively, the input node only has output wires
): Node(emptyList(), listOf(InterfaceDescription(name, interfaceStructure, interfaceType))) {
    override fun toString() = genToStringFromProperties(
        instance = this,
        ModuleInputNode::name,
        ModuleInputNode::outputs,
    )
}

class ModuleOutputNode(
    val name: String,
    val interfaceStructure: InterfaceStructure,
    val interfaceType: InterfaceType,
    // A bit counter-intuitively, the output node only has input wires
): Node(listOf(InterfaceDescription(name, interfaceStructure, interfaceType)), emptyList()) {
    override fun toString(): String = genToStringFromProperties(
        instance = this,
        ModuleOutputNode::name,
        ModuleOutputNode::inputs,
    )
}

class PassThroughNode(
    val interfaceStructures: List<InterfaceDescription>,
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

    val functionInputInterfaces: List<InterfaceDescription>,
    val functionOutputInterfaces: List<InterfaceDescription>,
): Node(
    inputInterface = functionInputInterfaces.map {
        InterfaceDescription(
            name = "${invokedModuleName}_${it.name}",
            interfaceStructure = it.interfaceStructure,
            interfaceType = it.interfaceType,
        )
    },
    outputInterface = functionOutputInterfaces.map {
        InterfaceDescription(
            name = "${invokedModuleName}_${it.name}",
            interfaceStructure = it.interfaceStructure,
            interfaceType = it.interfaceType,
        )
    },
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
    inputInterface = predefinedFunction.inputs.map {
        InterfaceDescription(
            name = "${invocationName}_${it.name}",
            interfaceStructure = it.interfaceStructure,
            interfaceType = it.interfaceType,
        )
    },
    outputInterface = predefinedFunction.outputs.map {
        InterfaceDescription(
            name = "${invocationName}_${it.name}",
            interfaceStructure = it.interfaceStructure,
            interfaceType = it.interfaceType,
        )
    }
)