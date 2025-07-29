package com.uabutler.verilogir.builder.creator

import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.verilogir.builder.creator.util.Declarations
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference

object PassThroughNodeCreator {
    fun create(node: PassThroughNode): List<Statement> = buildList {
        addAll(Declarations.create(node))

        node.inputWireVectorGroups.zip(node.outputWireVectorGroups).flatMap { (input, output) ->
            input.wireVectors.zip(output.wireVectors)
        }.forEach { (input, output) ->
            add(
                Assignment(
                    destReference = Reference(Identifier.wire(output)),
                    expression = Reference(Identifier.wire(input)),
                )
            )
        }
    }
}