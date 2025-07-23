package com.uabutler.netlistir.builder

import com.uabutler.gaplir.node.nodeinterface.NodeInputRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputVectorInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputWireInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentNode
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentVectorInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputVectorInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputWireInterface
import com.uabutler.gaplir.node.nodeinterface.NodeTopLevelInputInterface
import com.uabutler.gaplir.node.ModuleInvocationNode as GaplModuleInvocationNode
import com.uabutler.gaplir.node.PassThroughNode as GaplPassThroughNode
import com.uabutler.gaplir.node.PredefinedFunctionInvocationNode as GaplPredefinedFunctionInvocationNode
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.netlistir.netlist.InputWireVector
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.OutputWire
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
import com.uabutler.gaplir.node.Node as GaplNode
import com.uabutler.gaplir.node.BodyNode as GaplBodyNode
import com.uabutler.gaplir.node.InputNode as GaplInputNode
import com.uabutler.gaplir.node.OutputNode as GaplOutputNode
import com.uabutler.netlistir.netlist.Node as NetlistNode
import com.uabutler.netlistir.netlist.BodyNode as NetlistBodyNode
import com.uabutler.netlistir.netlist.InputNode as NetlistInputNode
import com.uabutler.netlistir.netlist.OutputNode as NetlistOutputNode

object Builder {

    private data class NodePair(
        val gaplNode: GaplNode,
        val netlistNode: NetlistNode,
    )

    private fun connectModuleUsingGaplIR(module: NetlistModule, nodePairs: List<NodePair>) {
        // Create a map for efficient lookup of netlist nodes by gapl nodes
        val nodeMap = nodePairs.associate { it.gaplNode to it.netlistNode }

        // Process each node pair to create connections
        for (nodePair in nodePairs) {
            val gaplNode = nodePair.gaplNode
            val netlistNode = nodePair.netlistNode

            // Process all inputs of the GaplNode
            for (inputVariable in gaplNode.inputs) {
                // Each top-level input interface corresponds to approximately a variable in gapl. As does each wire vector group.
                val netlistInputGroup = netlistNode.inputWireVectorGroups[inputVariable.name]!!

                // Process these in pairs.
                connectVariable(inputVariable.item, netlistInputGroup, nodeMap, module)
            }
        }
    }

    /* At a high level, this connects variables in gapl. Variables in Netlist IR are roughly represented by wire vector
     * groups, while in GAPL IR, they're roughly represented by top-level input interfaces.
     */
    private fun connectVariable(
        gaplVariable: NodeTopLevelInputInterface,
        netlistInputGroup: InputWireVectorGroup,
        nodeMap: Map<GaplNode, NetlistNode>,
        module: NetlistModule
    ) {
        val gaplInterface = gaplVariable.inputInterface

        when (gaplInterface) {
            is NodeInputWireInterface -> {
                // Get the source of this input (the output that feeds it)
                val sourceOutput = gaplInterface.input ?: return

                // Find the netlist wire that corresponds to this input
                // Assuming there's only one wire in the group for a simple wire interface
                val inputWire = netlistInputGroup.wireVectors.values.firstOrNull()?.wires?.firstOrNull() ?: return

                // Find the source output wire in the netlist
                val sourceOutputWire = findSourceOutputWire(sourceOutput, nodeMap) ?: return

                // Connect the wires in the netlist
                module.connect(inputWire, sourceOutputWire)
            }
            is NodeInputRecordInterface -> {
                // Process each field in the record
                for ((fieldName, fieldInterface) in gaplInterface.ports) {
                    val netlistFieldVector = netlistInputGroup.wireVectors[fieldName] ?: continue
                    connectVariable(fieldInterface, InputWireVectorGroup(
                        identifier = fieldName,
                        parentNode = netlistInputGroup.parentNode,
                        wireVectorsBuilder = { group -> listOf(netlistFieldVector) }
                    ), nodeMap, module)
                }
            }
            is NodeInputVectorInterface -> {
                // Process each element in the vector
                for (i in gaplInterface.vector.indices) {
                    val vectorElement = gaplInterface.vector[i]
                    // Find corresponding netlist wire vector
                    netlistInputGroup.wireVectors.values.forEach { wireVector ->
                        if (i < wireVector.wires.size) {
                            // Create a temporary group to process this single element
                            val elementGroup = InputWireVectorGroup(
                                identifier = "${netlistInputGroup.identifier}[$i]",
                                parentNode = netlistInputGroup.parentNode,
                                wireVectorsBuilder = { group ->
                                    listOf(InputWireVector(
                                        identifier = wireVector.identifier,
                                        parentGroup = group,
                                        wiresBuilder = { vector -> listOf(wireVector.wires[i]) }
                                    ))
                                }
                            )
                            connectVariable(vectorElement, elementGroup, nodeMap, module)
                        }
                    }
                }
            }
        }
    }

    private fun findSourceOutputWire(
        gaplOutput: NodeOutputWireInterface,
        nodeMap: Map<GaplNode, NetlistNode>
    ): OutputWire? {
        // Navigate up to find the parent node
        val parentNode = when (val parent = gaplOutput.parent) {
            is NodeOutputInterfaceParentNode -> {
                // This is a top-level output, find the corresponding node
                val gaplNode = parent.node
                val outputIndex = parent.outputIndex
                val outputName = gaplNode.outputInterface[outputIndex].name

                // Get the corresponding netlist node
                val netlistNode = nodeMap[gaplNode] ?: return null

                // Get the corresponding output wire vector group
                val outputGroup = netlistNode.outputWireVectorGroups[outputName] ?: return null

                // Get the first wire from the group (assuming simple wire output)
                return outputGroup.wireVectors.values.firstOrNull()?.wires?.firstOrNull()
            }
            is NodeOutputInterfaceParentRecordInterface -> {
                // This is a field in a record, need to recurse up
                val recordInterface = parent.recordInterface
                val fieldName = parent.fieldName

                // Find the parent node of the record
                findParentOutputWire(recordInterface, fieldName, nodeMap)
            }
            is NodeOutputInterfaceParentVectorInterface -> {
                // This is an element in a vector, need to recurse up
                val vectorInterface = parent.vectorInterface
                val index = parent.index

                // Find the parent node of the vector
                findParentVectorOutputWire(vectorInterface, index, nodeMap)
            }
            else -> null
        }

        return parentNode
    }

    private fun findParentOutputWire(
        recordInterface: NodeOutputRecordInterface,
        fieldName: String,
        nodeMap: Map<GaplNode, NetlistNode>
    ): OutputWire? {
        // Navigate up to find the parent node of the record
        val parentNode = when (val parent = recordInterface.parent) {
            is NodeOutputInterfaceParentNode -> {
                // This is a top-level output
                val gaplNode = parent.node
                val outputIndex = parent.outputIndex
                val outputName = gaplNode.outputInterface[outputIndex].name

                // Get the corresponding netlist node
                val netlistNode = nodeMap[gaplNode] ?: return null

                // Get the corresponding output wire vector group
                val outputGroup = netlistNode.outputWireVectorGroups[outputName] ?: return null

                // Get the field wire from the group
                return outputGroup.wireVectors[fieldName]?.wires?.firstOrNull()
            }
            else -> return null // Handle other cases if needed
        }

        return parentNode
    }

    private fun findParentVectorOutputWire(
        vectorInterface: NodeOutputVectorInterface,
        index: Int,
        nodeMap: Map<GaplNode, NetlistNode>
    ): OutputWire? {
        // Navigate up to find the parent node of the vector
        val parentNode = when (val parent = vectorInterface.parent) {
            is NodeOutputInterfaceParentNode -> {
                // This is a top-level output
                val gaplNode = parent.node
                val outputIndex = parent.outputIndex
                val outputName = gaplNode.outputInterface[outputIndex].name

                // Get the corresponding netlist node
                val netlistNode = nodeMap[gaplNode] ?: return null

                // Get the corresponding output wire vector group
                val outputGroup = netlistNode.outputWireVectorGroups[outputName] ?: return null

                // Get the indexed wire from the group
                return outputGroup.wireVectors.values.firstOrNull()?.wires?.getOrNull(index)
            }
            else -> return null // Handle other cases if needed
        }

        return parentNode
    }

    fun buildModule(gaplModule: GaplModule): NetlistModule {
        // Create an initial version of the module without any connections
        val nodePairs = mutableListOf<NodePair>()

        val module = NetlistModule(
            // TODO: Is this the right way to do this? Should we just keep the invocation information and compare it directly?
            identifier = ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplModule.moduleInvocation),
            inputNodesBuilder = { parentModule ->
                gaplModule.inputNodes.map { gaplNode ->
                    buildInputNode(gaplNode, parentModule).also { netlistNode ->
                        nodePairs.add(NodePair(gaplNode, netlistNode))
                    }
                }
            },
            outputNodesBuilder = { parentModule ->
                gaplModule.outputNodes.map { gaplNode ->
                    buildOutputNode(gaplNode, parentModule).also { netlistNode ->
                        nodePairs.add(NodePair(gaplNode, netlistNode))
                    }
                }
            },
            childNodesBuilder = { parentModule ->
                gaplModule.bodyNodes.map { gaplNode ->
                    buildBodyNode(gaplNode, parentModule).also { netlistNode ->
                        nodePairs.add(NodePair(gaplNode, netlistNode))
                    }
                }
            },
        )

        // Connect all the nodes in the module using GaplIR information
        connectModuleUsingGaplIR(module, nodePairs)

        return module
    }

    private fun buildInputNode(gaplNode: GaplInputNode, parent: NetlistModule): NetlistInputNode {
        TODO()
    }

    private fun buildOutputNode(gaplNode: GaplOutputNode, parent: NetlistModule): NetlistOutputNode {
        TODO()
    }

    private fun buildBodyNode(gaplNode: GaplBodyNode, parent: NetlistModule): NetlistBodyNode {
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