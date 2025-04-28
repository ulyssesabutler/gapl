package com.uabutler.verilogir.builder.node

import com.uabutler.gaplir.builder.util.LiteralFunction
import com.uabutler.gaplir.node.PredefinedFunctionInvocationNode
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.IntLiteral
import com.uabutler.verilogir.module.statement.expression.Reference

object LiteralConnector {
    fun connect(node: PredefinedFunctionInvocationNode, predefinedFunction: LiteralFunction): List<Statement> {
        val output = VerilogInterface.fromGAPLInterfaceStructure("${node.invocationName}_value_output", predefinedFunction.storageStructure).first()

        return listOf(
            Assignment(
                destReference = Reference(
                    variableName = output.name,
                    startIndex = 0,
                    endIndex = output.width - 1,
                ),
                expression = IntLiteral(predefinedFunction.value)
            )
        )
    }
}