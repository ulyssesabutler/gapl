package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.verilogir.builder.creator.ModuleInvocationNodeCreator
import com.uabutler.verilogir.builder.creator.PassThroughNodeCreator
import com.uabutler.verilogir.builder.creator.PredefinedFunctionNodeCreator
import com.uabutler.verilogir.builder.node.PassThroughNodeConnector
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
        return when (node) {
            is InputNode -> emptyList() // Implicitly created
            is OutputNode -> emptyList() // Implicitly created
            is ModuleInvocationNode -> ModuleInvocationNodeCreator.create(node)
            is PassThroughNode -> PassThroughNodeCreator.create(node)
            is PredefinedFunctionNode -> PredefinedFunctionNodeCreator.create(node)
        }
    }

    private fun connectNodes(node: Node): List<Statement> {
        node.inputWireVectors().forEach { wireVector ->

        }
    }

}