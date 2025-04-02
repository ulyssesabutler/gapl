package com.uabutler.verilogir.builder

import com.uabutler.gaplir.builder.util.*
import com.uabutler.gaplir.node.*
import com.uabutler.gaplir.node.input.*
import com.uabutler.gaplir.node.output.NodeOutputInterface
import com.uabutler.gaplir.node.output.NodeOutputInterfaceParentNode
import com.uabutler.gaplir.node.output.NodeOutputInterfaceParentRecordInterface
import com.uabutler.gaplir.node.output.NodeOutputInterfaceParentVectorInterface
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.builder.node.BinaryOperationConnector
import com.uabutler.verilogir.builder.node.ModuleInvocationConnector
import com.uabutler.verilogir.builder.node.PassThroughNodeConnector
import com.uabutler.verilogir.builder.node.RegisterConnector
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.util.DataType

object StatementBuilder {

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
        val inputs = node.inputs.map { VerilogInterface.fromGAPLInterfaceStructure("${it.name}_input", it.item.structure) }
        val outputs = node.outputs.map { VerilogInterface.fromGAPLInterfaceStructure("${it.name}_output", it.item.structure) }

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
                is AdditionFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is LeftShiftFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is MultiplicationFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is RightShiftFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is SubtractionFunction -> BinaryOperationConnector.connect(node, node.predefinedFunction)
                is RegisterFunction -> RegisterConnector.connect(node, node.predefinedFunction)
            }
            else -> emptyList()
        }
    }

    private fun internodeConnections(node: Node): List<Statement> {
        return getAssignments(node)
    }

    private fun getAssignments(node: Node): List<Assignment> {
        return node.inputs.flatMap {
            getConnectionsFromNodeInputInterface(
                currentName = "${it.name}_input",
                currentIndex = 0,
                currentSize = 1,
                nodeInputInterface = it.item,
            )
        }.map {
            ConnectionWithOutput(
                connection = it,
                currentOutputLocation = getNodeOutputInterfaceLocation(
                    currentName = null,
                    currentSize = 1,
                    currentIndex = 0,
                    nodeOutputInterface = it.output,
                )
            )
        }.flatMap { connection ->
            val src = VerilogInterface.fromGAPLInterfaceStructure(
                name = connection.currentOutputLocation.name,
                gaplInterfaceStructure = connection.connection.output.structure,
            )
            val srcSize = connection.currentOutputLocation.size
            val srcIndex = connection.currentOutputLocation.index

            val dest = VerilogInterface.fromGAPLInterfaceStructure(
                name = connection.connection.currentInputLocation.name,
                gaplInterfaceStructure = connection.connection.output.structure,
            )
            val destSize = connection.connection.currentInputLocation.size
            val destIndex = connection.connection.currentInputLocation.index

            src.zip(dest).map { (src, dest) ->
                var destStartIndexOffset = destIndex * dest.width
                var destEndIndexOffset = destIndex * dest.width
                var srcStartIndexOffset = srcIndex * src.width
                var srcEndIndexOffset = srcIndex * src.width

                connection.connection.vectorConnection?.let {
                    if (it.destSlice is VectorSlice) {
                        destStartIndexOffset += it.destSlice.startIndex
                        destEndIndexOffset += it.destSlice.endIndex
                    } else {
                        destStartIndexOffset += dest.width - 1
                    }

                    if (it.sourceSlice is VectorSlice) {
                        srcStartIndexOffset += it.sourceSlice.startIndex
                        srcEndIndexOffset += it.sourceSlice.endIndex
                    } else {
                        srcStartIndexOffset += src.width - 1
                    }
                }

                Assignment(
                    destReference = Reference(
                        variableName = dest.name,
                        startIndex = destStartIndexOffset, // TODO: Is this right?
                        endIndex = destEndIndexOffset,
                    ),
                    expression = Reference(
                        variableName = src.name,
                        startIndex = srcStartIndexOffset, // TODO: Is this right?
                        endIndex = srcEndIndexOffset,
                    )
                )
            }
        }
    }

    data class Location(
        val name: String,
        val index: Int,
        val size: Int,
    )

    data class Connection(
        val currentInputLocation: Location,
        val input: NodeInputInterface,
        val output: NodeOutputInterface,
        val vectorConnection: VectorConnection? = null,
    )

    data class ConnectionWithOutput(
        val connection: Connection,
        val currentOutputLocation: Location,
    )


    private fun getNodeOutputInterfaceLocation(currentName: String?, currentIndex: Int, currentSize: Int, nodeOutputInterface: NodeOutputInterface): Location {
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

    private fun getConnectionsFromNodeInputInterface(currentName: String, currentIndex: Int, currentSize: Int, nodeInputInterface: NodeInputInterface): List<Connection> {
        return when (nodeInputInterface) {
            is NodeInputWireInterface -> {
                // Otherwise, this interface has no input, which we're considering an error in GAPL
                assert(nodeInputInterface.input != null)
                listOf(
                    Connection(
                        currentInputLocation = Location(
                            name = currentName,
                            index = currentIndex,
                            size = currentSize,
                        ),
                        input = nodeInputInterface,
                        output = nodeInputInterface.input!!,
                    )
                )
            }
            is NodeInputRecordInterface -> {
                if (nodeInputInterface.input != null) {
                    listOf(
                        Connection(
                            currentInputLocation = Location(
                                name = currentName,
                                index = currentIndex,
                                size = currentSize,
                            ),
                            input = nodeInputInterface,
                            output = nodeInputInterface.input!!,
                        )
                    )
                } else {
                    nodeInputInterface.ports.flatMap {
                        getConnectionsFromNodeInputInterface(
                            currentName = "${currentName}_${it.key}",
                            currentIndex = currentIndex,
                            currentSize = currentSize,
                            nodeInputInterface = it.value,
                        )
                    }
                }
            }
            is NodeInputVectorInterface -> {
                val isConnected = BooleanArray(nodeInputInterface.structure.size) { false }

                val currentLevelConnections = nodeInputInterface.connections.map { connection ->
                    if (connection.sourceSlice is WholeVector) {
                        // Otherwise, connection overlap
                        assert(isConnected.all { !it })
                        // Mark the whole thing as connected now
                        isConnected.forEachIndexed { index, _ -> isConnected[index] = true }

                        Connection(
                            currentInputLocation = Location(
                                name = currentName,
                                index = currentIndex,
                                size = currentSize,
                            ),
                            input = nodeInputInterface,
                            output = connection.sourceVector,
                            vectorConnection = connection,
                        )
                    } else {
                        val start = (connection.sourceSlice as VectorSlice).startIndex
                        val end = connection.sourceSlice.endIndex

                        // Validation
                        for (i in start..end) {
                            // Check for overlap
                            assert(!isConnected[i])
                            // Then set
                            isConnected[i] = true
                        }

                        Connection(
                            currentInputLocation = Location(
                                name = currentName,
                                index = currentIndex,
                                size = currentSize,
                            ),
                            input = nodeInputInterface,
                            output = connection.sourceVector,
                            vectorConnection = connection,
                        )
                    }
                }

                val lowerLevelConnections = isConnected.mapIndexed { index, connection ->
                    if (!connection) {
                        getConnectionsFromNodeInputInterface(
                            currentName = currentName,
                            currentIndex = currentIndex * nodeInputInterface.structure.size + index,
                            currentSize = currentSize * nodeInputInterface.structure.size,
                            nodeInputInterface = nodeInputInterface.vector[index],
                        )
                    } else {
                        null
                    }
                }.filterNotNull().flatten()

                currentLevelConnections + lowerLevelConnections
            }
        }
    }
}