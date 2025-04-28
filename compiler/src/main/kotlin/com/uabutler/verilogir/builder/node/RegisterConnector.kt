package com.uabutler.verilogir.builder.node

import com.uabutler.gaplir.builder.util.RegisterFunction
import com.uabutler.gaplir.node.PredefinedFunctionInvocationNode
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.module.statement.Always
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.always.Clocked
import com.uabutler.verilogir.module.statement.always.IfBranch
import com.uabutler.verilogir.module.statement.always.IfStatement
import com.uabutler.verilogir.module.statement.always.NonBlockingAssignment
import com.uabutler.verilogir.module.statement.expression.IntLiteral
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.util.DataType

object RegisterConnector {
    fun connect(node: PredefinedFunctionInvocationNode, predefinedFunction: RegisterFunction): List<Statement> {
        val inputs = VerilogInterface.fromGAPLInterfaceStructure("${node.invocationName}_next_input", predefinedFunction.storageStructure)
        val outputs = VerilogInterface.fromGAPLInterfaceStructure("${node.invocationName}_current_output", predefinedFunction.storageStructure)

        val registers = VerilogInterface.fromGAPLInterfaceStructure("${node.invocationName}_register", predefinedFunction.storageStructure)

        val declarations = registers.map {
            Declaration(
                name = it.name,
                type = DataType.REG,
                startIndex = it.width - 1,
                endIndex = 0,
            )
        }

        val assignments = outputs.zip(registers).map { (output, register) ->
            Assignment(
                destReference = Reference(
                    variableName = output.name,
                    startIndex = output.width - 1,
                    endIndex = 0,
                ),
                expression = Reference(
                    variableName = register.name,
                    startIndex = register.width - 1,
                    endIndex = 0,
                )
            )
        }

        val always = Always(
            sensitivity = Clocked("clock"),
            statements = listOf(
                IfStatement(
                    ifBranch = IfBranch(
                        condition = Reference(
                            variableName = "reset",
                            startIndex = null,
                            endIndex = null,
                        ),
                        then = registers.map { register ->
                            NonBlockingAssignment(
                                variableName = register.name,
                                expression = IntLiteral(0) // TODO
                            )
                        }
                    ),
                    elseIfBranches = emptyList(),
                    elseStatements = registers.zip(inputs).map { (register, input) ->
                        NonBlockingAssignment(
                            variableName = register.name,
                            expression = Reference(
                                variableName = input.name,
                                startIndex = input.width - 1,
                                endIndex = 0,
                            )
                        )
                    }
                )
            )
        )

        return declarations + assignments + always
    }
}