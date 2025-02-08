package com.uabutler.verilogir.builder.node

import com.uabutler.gaplir.node.PassThroughNode
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference

object PassThroughNodeConnector {
    fun connect(node: PassThroughNode): List<Statement> {
        val inputWires = node.inputs.map {
            VerilogInterface.fromGAPLInterfaceStructure("${it.name}_inputs", it.item.structure)
        }.flatten()

        val outputWires = node.outputs.map {
            VerilogInterface.fromGAPLInterfaceStructure("${it.name}_outputs", it.item.structure)
        }.flatten()

        return inputWires.zip(outputWires).map { (input, output) ->
            Assignment(
                destReference = Reference(
                    variableName = output.name,
                    startIndex = 0,
                    endIndex = output.width - 1,
                ),
                expression = Reference(
                    variableName = input.name,
                    startIndex = 0,
                    endIndex = input.width - 1,
                )
            )
        }
    }
}