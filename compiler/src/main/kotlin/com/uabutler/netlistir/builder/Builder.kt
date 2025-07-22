package com.uabutler.netlistir.builder

import com.uabutler.gaplir.node.ModuleInvocationNode as GaplModuleInvocationNode
import com.uabutler.gaplir.node.PassThroughNode as GaplPassThroughNode
import com.uabutler.gaplir.node.PredefinedFunctionInvocationNode as GaplPredefinedFunctionInvocationNode
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.netlistir.netlist.IONode
import com.uabutler.netlistir.netlist.IOWireVector
import com.uabutler.netlistir.netlist.InputWireVector
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.ModuleInvocationNode as NetlistModuleInvocationNode
import com.uabutler.netlistir.netlist.OutputWireVector
import com.uabutler.netlistir.netlist.OutputWireVectorGroup
import com.uabutler.netlistir.util.PredefinedFunction
import com.uabutler.util.AnonymousIdentifierGenerator
import com.uabutler.netlistir.netlist.PredefinedFunctionNode as NetlistPredefinedFunctionNode
import com.uabutler.netlistir.netlist.PassThroughNode as NetlistPassThroughNode
import com.uabutler.util.VerilogInterface
import com.uabutler.verilogir.builder.identifier.ModuleIdentifierGenerator
import com.uabutler.gaplir.Module as GaplModule
import com.uabutler.netlistir.netlist.Module as NetlistModule
import com.uabutler.gaplir.node.BodyNode as GaplBodyNode
import com.uabutler.netlistir.netlist.Node as NetlistNode

object Builder {

    fun buildModule(gaplModule: GaplModule) = NetlistModule(
        // TODO: Is this the right way to do this? Should we just keep the invocation information and compare it directly?
        identifier = ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplModule.moduleInvocation),
        inputNodesBuilder = {
            gaplModule.inputNodes.map { inputNode ->
                val wires = VerilogInterface.fromGAPLInterfaceStructure(
                    structure = inputNode.interfaceStructure,
                )

                IONode(
                    identifier = inputNode.name,
                    wireVectors = wires.map {
                        IOWireVector(
                            identifier = it.name,
                            size = it.width,
                        )
                    }
                )
            }
        },
        outputNodesBuilder = {
            gaplModule.outputNodes.map { outputNode ->
                val wires = VerilogInterface.fromGAPLInterfaceStructure(
                    structure = outputNode.interfaceStructure,
                )

                IONode(
                    identifier = outputNode.name,
                    wireVectors = wires.map {
                        IOWireVector(
                            identifier = it.name,
                            size = it.width,
                        )
                    }
                )
            }
        },
        childNodesBuilder = { parentNode ->
            gaplModule.bodyNodes.map { buildBodyNode(it, parentNode) }
        }
    )

    private fun buildBodyNode(gaplNode: GaplBodyNode, parent: NetlistModule): NetlistNode {
        return when (gaplNode) {

            is GaplModuleInvocationNode -> {
                NetlistModuleInvocationNode(
                    identifier = gaplNode.invokedModuleName,
                    parentModule = parent,
                    inputWireVectorGroupsBuilder = { node ->
                        gaplNode.inputInterface.map { buildInputWireVectorGroups(it, node) }
                    },
                    outputWireVectorGroupsBuilder = { node ->
                        gaplNode.outputInterface.map { buildOutputWireVectorGroups(it, node) }
                    },
                    invokedModuleIdentifier = ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplNode.moduleInvocation)
                )
            }

            is GaplPassThroughNode -> {
                NetlistPassThroughNode(
                    identifier = AnonymousIdentifierGenerator.genIdentifier(),
                    parentModule = parent,
                    inputWireVectorGroupsBuilder = { node ->
                        gaplNode.inputInterface.map { buildInputWireVectorGroups(it, node) }
                    },
                    outputWireVectorGroupsBuilder = { node ->
                        gaplNode.outputInterface.map { buildOutputWireVectorGroups(it, node) }
                    }
                )
            }

            is GaplPredefinedFunctionInvocationNode -> {
                NetlistPredefinedFunctionNode(
                    identifier = gaplNode.invocationName,
                    parentModule = parent,
                    inputWireVectorGroupsBuilder = { node ->
                        gaplNode.inputInterface.map { buildInputWireVectorGroups(it, node) }
                    },
                    outputWireVectorGroupsBuilder = { node ->
                        gaplNode.outputInterface.map { buildOutputWireVectorGroups(it, node) }
                    },
                    predefinedFunction = PredefinedFunction.fromGapl(gaplNode.predefinedFunction)
                )
            }

        }
    }

    private fun buildInputWireVectorGroups(interfaceDescription: InterfaceDescription, parent: NetlistNode) = InputWireVectorGroup(
        identifier = interfaceDescription.name,
        parentNode = parent,
        wireVectorsBuilder = { wireVectorGroup ->
            VerilogInterface.fromGAPLInterfaceStructure(interfaceDescription.interfaceStructure)
                .map { buildInputWireVectors(it, wireVectorGroup) }
        },
    )


    private fun buildOutputWireVectorGroups(interfaceDescription: InterfaceDescription, parent: NetlistNode) = OutputWireVectorGroup(
        identifier = interfaceDescription.name,
        parentNode = parent,
        wireVectorsBuilder = { wireVectorGroup ->
            VerilogInterface.fromGAPLInterfaceStructure(interfaceDescription.interfaceStructure)
                .map { buildOutputWireVectors(it, wireVectorGroup) }
        },
    )

    private fun buildInputWireVectors(verilogWire: VerilogInterface.Wire, parent: InputWireVectorGroup) = InputWireVector(
        identifier = verilogWire.name,
        parentGroup = parent,
        size = verilogWire.width,
    )

    private fun buildOutputWireVectors(verilogWire: VerilogInterface.Wire, parent: OutputWireVectorGroup) = OutputWireVector(
        identifier = verilogWire.name,
        parentGroup = parent,
        size = verilogWire.width,
    )

}