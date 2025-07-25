package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.Node
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.util.DataType

object StatementBuilder {

    fun verilogStatementsFromNodes(nodes: List<Node>): List<Statement> {
        val creations = nodes.flatMap { createNodes(it) }
        val connections = nodes.flatMap { connectNodes(it) }

        return creations + connections
    }

    private fun createNodes(node: Node): List<Statement> = buildList {
        node.inputWireVectorGroups.flatMap { group ->
            group.wireVectors.map { wire ->
                add(
                    Declaration(
                        name = "${node.identifier}$${group.identifier}$${wire.identifier.joinToString("$")}",
                        type = DataType.WIRE,
                        startIndex = wire.wires.size - 1,
                        endIndex = 0,
                    )
                )
            }
        }

        node.outputWireVectorGroups.flatMap { group ->
            group.wireVectors.map { wire ->
                add(
                    Declaration(
                        name = "${node.identifier}$${group.identifier}$${wire.identifier.joinToString("$")}",
                        type = DataType.WIRE,
                        startIndex = wire.wires.size - 1,
                        endIndex = 0,
                    )
                )
            }
        }

    }

    private fun connectNodes(node: Node): List<Statement> {
        return emptyList()
    }

}