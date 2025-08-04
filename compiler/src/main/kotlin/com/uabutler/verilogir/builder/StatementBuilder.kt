package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.InputWireVector
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWireVector
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.netlist.WireVector
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
    ) {
        override fun toString() = buildString {
            fun component(wire: WireVector<*>, range: IntRange) = buildString {
                append(Identifier.wire(wire).padEnd(25, ' '))
                append("[")
                append(range.first.toString().padStart(3, ' '))
                append(":")
                append(range.last.toString().padStart(3, ' '))
                append("]")
            }

            append(component(source, sourceRange))
            append(" -> ")
            append(component(destination, destinationRange))
        }
    }


    private fun getVerilogConnectionsForWireVector(wireVector: InputWireVector): List<VerilogConnection> = buildList {
        val module = wireVector.parentGroup.parentNode.parentModule

        var previousConnection = module.getConnectionForInputWire(wireVector.wires.first())

        var currentSourceStartIndex = previousConnection.source.index
        var currentDestinationStartIndex = previousConnection.sink.index // Always 0

        fun addPrevious() {
            val connection = VerilogConnection(
                source = previousConnection.source.parentWireVector,
                sourceRange = currentSourceStartIndex..previousConnection.source.index,
                destination = previousConnection.sink.parentWireVector,
                destinationRange = currentDestinationStartIndex..previousConnection.sink.index,
            )

            add(connection)
        }

        wireVector.wires.drop(1).forEach { wire ->
            val currentConnection = module.getConnectionForInputWire(wire)

            if (currentConnection.source != previousConnection.source || currentConnection.source.index != previousConnection.source.index + 1) {
                addPrevious()

                currentSourceStartIndex = currentConnection.source.index
                currentDestinationStartIndex = currentConnection.sink.index
            }

            previousConnection = currentConnection
        }

        addPrevious()
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