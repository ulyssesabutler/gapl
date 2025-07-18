package com.uabutler.verilogir.builder

import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.builder.util.*
import com.uabutler.gaplir.node.*
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterfaceParentNode
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterfaceParentRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputInterfaceParentVectorInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputVectorInterface
import com.uabutler.gaplir.node.nodeinterface.NodeInputWireInterface
import com.uabutler.gaplir.node.nodeinterface.VectorSlice
import com.uabutler.gaplir.node.nodeinterface.WholeVector
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentNode
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentRecordInterface
import com.uabutler.gaplir.node.nodeinterface.NodeOutputInterfaceParentVectorInterface
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.builder.node.*
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.util.DataType

object StatementBuilder {

    fun verilogStatementsFromIONodes(inputs: List<ModuleInputNode>, outputs: List<ModuleOutputNode>): List<Statement> {
        val inputIOWires = inputs.flatMap { node ->
            VerilogInterface.fromGAPLInterfaceStructure(
                name = node.name,
                gaplInterfaceStructure = node.interfaceStructure,
            )
        }

        val inputNodeWires = inputs.flatMap { node ->
            VerilogInterface.fromGAPLInterfaceStructure(
                name = "${node.name}_output",
                gaplInterfaceStructure = node.interfaceStructure,
            )
        }

        val outputIOWires = outputs.flatMap { node ->
            VerilogInterface.fromGAPLInterfaceStructure(
                name = node.name,
                gaplInterfaceStructure = node.interfaceStructure,
            )
        }

        val outputNodeWires = outputs.flatMap { node ->
            VerilogInterface.fromGAPLInterfaceStructure(
                name = "${node.name}_input",
                gaplInterfaceStructure = node.interfaceStructure,
            )
        }

        val inputDeclarations = inputNodeWires.map { wire ->
            Declaration(
                name = wire.name,
                type = DataType.WIRE,
                startIndex = wire.width - 1,
                endIndex = 0,
            )
        }

        val outputDeclarations = outputNodeWires.map { wire ->
            Declaration(
                name = wire.name,
                type = DataType.WIRE,
                startIndex = wire.width - 1,
                endIndex = 0,
            )
        }

        val inputsAssignments = inputIOWires.zip(inputNodeWires).map { (io, node) ->
            Assignment(
                destReference = Reference(
                    variableName = node.name,
                    startIndex = null,
                    endIndex = null,
                ),
                expression = Reference(
                    variableName = io.name,
                    startIndex = null,
                    endIndex = null,
                )
            )
        }

        val outputsAssignments = outputIOWires.zip(outputNodeWires).map { (io, node) ->
            Assignment(
                destReference = Reference(
                    variableName = io.name,
                    startIndex = null,
                    endIndex = null,
                ),
                expression = Reference(
                    variableName = node.name,
                    startIndex = null,
                    endIndex = null,
                )
            )
        }

        return inputDeclarations + outputDeclarations +  inputsAssignments + outputsAssignments
    }

    fun verilogStatementsFromGAPLNodes(nodes: List<Node>): List<Statement> {
        val declarations = nodes
            .filter { it !is ModuleOutputNode }
            .flatMap { node -> declareNodeWires(node) }

        val connections = nodes.flatMap { node ->
            intranodeConnections(node) + internodeConnections(node)
        }

        return declarations + connections
    }

    private fun declareNodeWires(node: Node): List<Statement> {
        // TODO: Move computing identifiers
        val inputs = node.inputs.map { VerilogInterface.fromGAPLInterfaceStructure("${it.name}_input", it.item.inputInterface.structure) }
        val outputs = node.outputs.map { VerilogInterface.fromGAPLInterfaceStructure("${it.name}_output", it.item.outputInterface.structure) }

        return (inputs + outputs).flatten().map { wire ->
            Declaration(
                name = wire.name,
                type = DataType.WIRE,
                startIndex = wire.width - 1,
                endIndex = 0,
            )
        }
    }

    private fun intranodeConnections(node: Node): List<Statement> {
        return when (node) {
            is PassThroughNode -> PassThroughNodeConnector.connect(node)
            is ModuleInvocationNode -> ModuleInvocationConnector.connect(node)
            is PredefinedFunctionInvocationNode -> when (node.predefinedFunction) {
                is EqualsFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is GreaterThanEqualsFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is LessThanEqualsFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is NotEqualsFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is AndFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is OrFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is BitwiseAndFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is BitwiseOrFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is BitwiseXorFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is AdditionFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is LeftShiftFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is MultiplicationFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is RightShiftFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is SubtractionFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is RegisterFunction -> RegisterConnector.connect(node, node.predefinedFunction)
                is LiteralFunction -> LiteralConnector.connect(node, node.predefinedFunction)
            }
            else -> emptyList()
        }
    }

    private fun internodeConnections(node: Node): List<Statement> {
        return getAssignments(node)
    }

    private fun getAssignments(node: Node): List<Assignment> {
        return node.inputs
            .flatMap { getNodeInputInterfaceConnections(it.item.inputInterface) }
            .flatMap { generateAssignmentsForConnection(it) }
    }

    sealed class AbstractConnection(
        open val nodeInputInterface: NodeInputInterface,
        open val nodeOutputInterface: NodeOutputInterface,
    )

    data class Connection(
        override val nodeInputInterface: NodeInputInterface,
        override val nodeOutputInterface: NodeOutputInterface,
    ): AbstractConnection(nodeInputInterface, nodeOutputInterface)

    data class VectorConnection(
        override val nodeInputInterface: NodeInputInterface,
        override val nodeOutputInterface: NodeOutputInterface,
        val vectorConnection: com.uabutler.gaplir.node.nodeinterface.VectorConnection,
    ): AbstractConnection(nodeInputInterface, nodeOutputInterface)

    private fun getNodeInputInterfaceConnections(nodeInputInterface: NodeInputInterface): List<AbstractConnection> {
        return when (nodeInputInterface) {

            is NodeInputWireInterface -> {
                listOfNotNull(
                    nodeInputInterface.input?.let {
                        Connection(
                            nodeInputInterface = nodeInputInterface,
                            nodeOutputInterface = it,
                        )
                    }
                )
            }

            is NodeInputRecordInterface -> {
                val topLevel = listOfNotNull(
                    nodeInputInterface.input?.let {
                        Connection(
                            nodeInputInterface = nodeInputInterface,
                            nodeOutputInterface = it,
                        )
                    }
                )

                val lowerLevels = nodeInputInterface.ports.flatMap { getNodeInputInterfaceConnections(it.value) }

                topLevel + lowerLevels
            }

            is NodeInputVectorInterface -> {
                val topLevel = nodeInputInterface.connections.map {
                    VectorConnection(
                        nodeInputInterface = nodeInputInterface,
                        nodeOutputInterface = it.sourceVector,
                        vectorConnection = it,
                    )
                }

                val lowerLevels = nodeInputInterface.vector.flatMap { getNodeInputInterfaceConnections(it) }

                topLevel + lowerLevels
            }

        }
    }

    private fun generateAssignmentsForConnection(connection: AbstractConnection): List<Assignment> {
        val sinkLocation = getNodeInputInterfaceLocation(connection.nodeInputInterface)
        val sourceLocation = getNodeOutputInterfaceLocation(connection.nodeOutputInterface)

        return when(connection) {
            is Connection -> {
                val sinkWires = VerilogInterface.fromGAPLInterfaceStructure(
                    name = sinkLocation.name,
                    gaplInterfaceStructure = connection.nodeInputInterface.structure,
                )
                val sourceWires = VerilogInterface.fromGAPLInterfaceStructure(
                    name = sourceLocation.name,
                    gaplInterfaceStructure = connection.nodeOutputInterface.structure,
                )

                sinkWires.zip(sourceWires).map { (sink, source) ->
                    Assignment(
                        destReference = Reference(
                            variableName = sink.name,
                            startIndex = (sinkLocation.index + 1) * sink.width - 1,
                            endIndex = sinkLocation.index * sink.width,
                        ),
                        expression = Reference(
                            variableName = source.name,
                            startIndex = (sourceLocation.index + 1) * source.width - 1,
                            endIndex = sourceLocation.index * source.width,
                        )
                    )
                }
            }

            is VectorConnection -> {
                val vectorConnection = connection.vectorConnection

                val destinationReferences = when (vectorConnection.destSlice) {
                    is WholeVector -> {
                        VerilogInterface.fromGAPLInterfaceStructure(
                            name = sinkLocation.name,
                            gaplInterfaceStructure = connection.nodeInputInterface.structure,
                        ).map { sink ->
                            Reference(
                                variableName = sink.name,
                                startIndex = (sinkLocation.index + 1) * sink.width - 1,
                                endIndex = sinkLocation.index * sink.width,
                            )
                        }
                    }
                    is VectorSlice -> {
                        val sliceStartIndex = vectorConnection.destSlice.startIndex
                        val sliceEndIndex = vectorConnection.destSlice.endIndex

                        val vectoredStructure = (connection.nodeInputInterface.structure as VectorInterfaceStructure).vectoredInterface
                        val vectoredSize = (connection.nodeInputInterface.structure as VectorInterfaceStructure).size

                        VerilogInterface.fromGAPLInterfaceStructure(
                            name = sinkLocation.name,
                            gaplInterfaceStructure = vectoredStructure,
                        ).map { sink ->
                            Reference(
                                variableName = sink.name,
                                startIndex = (sinkLocation.index * vectoredSize + sliceEndIndex + 1) * sink.width - 1,
                                endIndex = (sinkLocation.index * vectoredSize + sliceStartIndex) * sink.width,
                            )
                        }
                    }
                }

                val sourceReferences = when (vectorConnection.sourceSlice) {
                    is WholeVector -> {
                        VerilogInterface.fromGAPLInterfaceStructure(
                            name = sourceLocation.name,
                            gaplInterfaceStructure = connection.nodeOutputInterface.structure,
                        ).map { source ->
                            Reference(
                                variableName = source.name,
                                startIndex = (sourceLocation.index + 1) * source.width - 1,
                                endIndex = sourceLocation.index * source.width,
                            )
                        }
                    }
                    is VectorSlice -> {
                        val sliceStartIndex = vectorConnection.sourceSlice.startIndex
                        val sliceEndIndex = vectorConnection.sourceSlice.endIndex

                        val vectoredStructure = (connection.nodeOutputInterface.structure as VectorInterfaceStructure).vectoredInterface
                        val vectoredSize = (connection.nodeOutputInterface.structure as VectorInterfaceStructure).size

                        VerilogInterface.fromGAPLInterfaceStructure(
                            name = sourceLocation.name,
                            gaplInterfaceStructure = vectoredStructure,
                        ).map { source ->
                            Reference(
                                variableName = source.name,
                                startIndex = (sourceLocation.index * vectoredSize + sliceEndIndex + 1) * source.width - 1,
                                endIndex = (sourceLocation.index * vectoredSize + sliceStartIndex) * source.width,
                            )
                        }
                    }
                }

                destinationReferences.zip(sourceReferences).map { (destination, source) ->
                    Assignment(
                        destReference = destination,
                        expression = source,
                    )
                }
            }
        }
    }

    data class Location(
        val name: String,
        val index: Int,
        val size: Int,
    )

    private fun getNodeInputInterfaceLocation(
        nodeInputInterface: NodeInputInterface,
        currentName: String? = null,
        currentIndex: Int = 0,
        currentSize: Int = 1,
    ): Location {
        return when (val parent = nodeInputInterface.parent) {
            is NodeInputInterfaceParentRecordInterface -> {
                getNodeInputInterfaceLocation(
                    currentName = listOfNotNull(parent.parentMember, currentName).joinToString("_"),
                    currentIndex = currentIndex,
                    currentSize = currentSize,
                    nodeInputInterface = parent.parentInterface
                )
            }
            is NodeInputInterfaceParentVectorInterface -> {
                getNodeInputInterfaceLocation(
                    currentName = currentName,
                    currentIndex = parent.parentIndex * currentSize + currentIndex,
                    currentSize = parent.parentInterface.vector.size * currentSize,
                    nodeInputInterface = parent.parentInterface
                )
            }
            is NodeInputInterfaceParentNode -> {
                Location(
                    name = listOfNotNull(parent.parentName, "input", currentName).joinToString("_"),
                    index = currentIndex,
                    size = currentSize,
                )
            }
        }
    }

    private fun getNodeOutputInterfaceLocation(
        nodeOutputInterface: NodeOutputInterface,
        currentName: String? = null,
        currentIndex: Int = 0,
        currentSize: Int = 1,
    ): Location {
        return when (val parent = nodeOutputInterface.parent) {
            is NodeOutputInterfaceParentRecordInterface -> {
                getNodeOutputInterfaceLocation(
                    currentName = listOfNotNull(parent.parentMember, currentName).joinToString("_"),
                    currentIndex = currentIndex,
                    currentSize = currentSize,
                    nodeOutputInterface = parent.parentInterface
                )
            }
            is NodeOutputInterfaceParentVectorInterface -> {
                getNodeOutputInterfaceLocation(
                    currentName = currentName,
                    currentIndex = parent.parentIndex * currentSize + currentIndex,
                    currentSize = parent.parentInterface.vector.size * currentSize,
                    nodeOutputInterface = parent.parentInterface
                )
            }
            is NodeOutputInterfaceParentNode -> {
                Location(
                    name = listOfNotNull(parent.parentName, "output", currentName).joinToString("_"),
                    index = currentIndex,
                    size = currentSize,
                )
            }
        }
    }

}
