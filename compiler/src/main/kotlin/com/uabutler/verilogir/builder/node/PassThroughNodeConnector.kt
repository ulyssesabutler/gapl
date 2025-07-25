package com.uabutler.verilogir.builder.node

import com.uabutler.util.VerilogInterface
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference

object PassThroughNodeConnector {
    fun connect(node: PassThroughNode): List<Statement> {
        val inputWires = node.inputs.map {
            VerilogInterface.fromGAPLInterfaceStructure(it.item.inputInterface.structure, "${it.name}_input")
        }.flatten()

        val outputWires = node.outputs.map {
            VerilogInterface.fromGAPLInterfaceStructure(it.item.outputInterface.structure, "${it.name}_output")
        }.flatten()

        return inputWires.zip(outputWires).map { (input, output) ->
            Assignment(
                destReference = Reference(
                    variableName = output.name,
                    startIndex = output.width - 1,
                    endIndex = 0,
                ),
                expression = Reference(
                    variableName = input.name,
                    startIndex = input.width - 1,
                    endIndex = 0,
                )
            )
        }
    }
}