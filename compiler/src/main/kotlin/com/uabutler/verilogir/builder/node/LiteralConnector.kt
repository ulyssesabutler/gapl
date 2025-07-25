package com.uabutler.verilogir.builder.node

import com.uabutler.util.VerilogInterface
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.IntLiteral
import com.uabutler.verilogir.module.statement.expression.Reference

object LiteralConnector {
    fun connect(node: PredefinedFunctionInvocationNode, predefinedFunction: LiteralFunction): List<Statement> {
        val output = VerilogInterface.fromGAPLInterfaceStructure(predefinedFunction.storageStructure, "${node.invocationName}_value_output").first()

        return listOf(
            Assignment(
                destReference = Reference(
                    variableName = output.name,
                    startIndex = output.width - 1,
                    endIndex = 0,
                ),
                expression = IntLiteral(predefinedFunction.value)
            )
        )
    }
}