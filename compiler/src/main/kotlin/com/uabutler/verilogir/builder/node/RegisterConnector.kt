package com.uabutler.verilogir.builder.node

import com.uabutler.util.VerilogInterface
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
        val inputs = VerilogInterface.fromGAPLInterfaceStructure(predefinedFunction.storageStructure, "${node.invocationName}_next_input")
        val outputs = VerilogInterface.fromGAPLInterfaceStructure(predefinedFunction.storageStructure, "${node.invocationName}_current_output")

        val registers = VerilogInterface.fromGAPLInterfaceStructure(predefinedFunction.storageStructure, "${node.invocationName}_register")

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
                    elseIfBranches = listOf(
                        IfBranch(
                            condition = Reference(
                                variableName = "enable",
                                startIndex = null,
                                endIndex = null,
                            ),
                            then = registers.zip(inputs).map { (register, input) ->
                                NonBlockingAssignment(
                                    variableName = register.name,
                                    expression = Reference(
                                        variableName = input.name,
                                        startIndex = input.width - 1,
                                        endIndex = 0,
                                    )
                                )
                            }
                        ),
                    ),
                    elseStatements = registers.map { register ->
                        NonBlockingAssignment(
                            variableName = register.name,
                            expression = Reference(
                                variableName = register.name,
                                startIndex = register.width - 1,
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