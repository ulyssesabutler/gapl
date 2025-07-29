package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.InputWireVector
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWireVector
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.verilogir.builder.creator.ModuleInvocationNodeCreator
import com.uabutler.verilogir.builder.creator.PassThroughNodeCreator
import com.uabutler.verilogir.builder.creator.PredefinedFunctionNodeCreator
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference

object StatementBuilder {

    fun verilogStatementsFromNodes(nodes: List<Node>): List<Statement> {
        val creations = nodes.flatMap { createNodes(it) }
        val connections = nodes.flatMap { connectNodes(it) }

        return creations + connections
    }

    private fun createNodes(node: Node): List<Statement> = buildList {
        return when (node) {
            is InputNode -> emptyList() // Implicitly created
            is OutputNode -> emptyList() // Implicitly created
            is ModuleInvocationNode -> ModuleInvocationNodeCreator.create(node)
            is PassThroughNode -> PassThroughNodeCreator.create(node)
            is PredefinedFunctionNode -> PredefinedFunctionNodeCreator.create(node)
        }
    }

    private data class VerilogConnection(
        val source: OutputWireVector,
        val sourceRange: IntRange,
        val destination: InputWireVector,
        val destinationRange: IntRange,
    )


    private fun getVerilogConnectionsForWireVector(wireVector: InputWireVector): List<VerilogConnection> = buildList {
        val module = wireVector.parentGroup.parentNode.parentModule

        data class Connection(
            val source: OutputWireVector,
            val sourceIndex: Int,
            val destination: InputWireVector,
            val destinationIndex: Int,
        )

        fun createConnection(wire: InputWire): Connection {
            val connection = module.getConnectionForInputWire(wire)
            return Connection(
                source = connection.outputWire.parentWireVector,
                sourceIndex = connection.outputWire.index,
                destination = connection.inputWire.parentWireVector,
                destinationIndex = connection.inputWire.index,
            )
        }

        val firstConnection = createConnection(wireVector.wires.first())

        var previousSourceIndex = firstConnection.sourceIndex
        var currentSourceStartIndex = firstConnection.sourceIndex
        var currentDestinationStartIndex = 0
        var previousSourceVector = firstConnection.source

        wireVector.wires.drop(1).forEach { wire ->
            val connection = createConnection(wire)
            val currentSourceIndex = connection.sourceIndex
            val currentSourceVector = connection.source

            if (currentSourceVector != previousSourceVector || currentSourceIndex != previousSourceIndex + 1) {
                add(
                    VerilogConnection(
                        source = connection.source,
                        sourceRange = currentSourceStartIndex until currentSourceIndex,
                        destination = connection.destination,
                        destinationRange = currentDestinationStartIndex until connection.destinationIndex,
                    )
                )

                currentSourceStartIndex = currentSourceIndex
                currentDestinationStartIndex = connection.destinationIndex
            }

            previousSourceIndex = currentSourceIndex
            previousSourceVector = currentSourceVector
        }
    }

    private fun connectNodes(node: Node): List<Statement> {
        return node.inputWireVectors().flatMap { wireVector ->
            getVerilogConnectionsForWireVector(wireVector).map { connection ->
                Assignment(
                    destReference = Reference(
                        variableName = Identifier.wire(connection.destination),
                        startIndex = connection.destinationRange.last,
                        endIndex = connection.destinationRange.first,
                    ),
                    expression = Reference(
                        variableName = Identifier.wire(connection.source),
                        startIndex = connection.sourceRange.last,
                        endIndex = connection.sourceRange.first,
                    )
                )
            }
        }
    }

}