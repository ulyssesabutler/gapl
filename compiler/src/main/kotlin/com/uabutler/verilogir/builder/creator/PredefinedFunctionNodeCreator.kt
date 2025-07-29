package com.uabutler.verilogir.builder.creator

import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.BinaryFunction
import com.uabutler.netlistir.util.BitwiseAndFunction
import com.uabutler.netlistir.util.BitwiseOrFunction
import com.uabutler.netlistir.util.BitwiseXorFunction
import com.uabutler.netlistir.util.EqualsFunction
import com.uabutler.netlistir.util.GreaterThanEqualsFunction
import com.uabutler.netlistir.util.LeftShiftFunction
import com.uabutler.netlistir.util.LessThanEqualsFunction
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.LogicalAndFunction
import com.uabutler.netlistir.util.LogicalOrFunction
import com.uabutler.netlistir.util.MultiplicationFunction
import com.uabutler.netlistir.util.NotEqualsFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.netlistir.util.RightShiftFunction
import com.uabutler.netlistir.util.SubtractionFunction
import com.uabutler.verilogir.builder.creator.util.Declarations
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.statement.Always
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.always.Clocked
import com.uabutler.verilogir.module.statement.always.IfBranch
import com.uabutler.verilogir.module.statement.always.IfStatement
import com.uabutler.verilogir.module.statement.always.NonBlockingAssignment
import com.uabutler.verilogir.module.statement.expression.BinaryOperation
import com.uabutler.verilogir.module.statement.expression.IntLiteral
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.module.statement.util.BinaryOperator
import com.uabutler.verilogir.util.DataType

object PredefinedFunctionNodeCreator {
    private fun binaryFunction(node: PredefinedFunctionNode): Statement {
        fun create(node: PredefinedFunctionNode, operator: BinaryOperator): Statement {
            if (node.inputWireVectors().size != 2 || node.outputWireVectors().size != 1) {
                throw Exception("Invalid number of inputs or outputs for binary function $operator")
            }

            val lhs = node.inputWireVectors()[0]
            val rhs = node.inputWireVectors()[1]
            val result = node.outputWireVectors()[0]

            return Assignment(
                destReference = Reference(Identifier.wire(result)),
                expression = BinaryOperation(
                    lhs = Reference(Identifier.wire(lhs)),
                    rhs = Reference(Identifier.wire(rhs)),
                    operator = operator,
                )
            )
        }

        return when (node.predefinedFunction as BinaryFunction) {
            is AdditionFunction -> create(node, BinaryOperator.ADD)
            is BitwiseAndFunction -> create(node, BinaryOperator.BITWISE_AND)
            is BitwiseOrFunction -> create(node, BinaryOperator.BITWISE_OR)
            is BitwiseXorFunction -> create(node, BinaryOperator.BITWISE_XOR)
            is LeftShiftFunction -> create(node, BinaryOperator.LEFT_SHIFT)
            is MultiplicationFunction -> create(node, BinaryOperator.MULTIPLY)
            is RightShiftFunction -> create(node, BinaryOperator.RIGHT_SHIFT)
            is SubtractionFunction -> create(node, BinaryOperator.SUBTRACT)
            is EqualsFunction -> create(node, BinaryOperator.EQUALS)
            is GreaterThanEqualsFunction -> create(node, BinaryOperator.GREATER_THAN_EQUALS)
            is LessThanEqualsFunction -> create(node, BinaryOperator.LESS_THAN_EQUALS)
            is NotEqualsFunction -> create(node, BinaryOperator.NOT_EQUALS)
            LogicalAndFunction -> create(node, BinaryOperator.AND)
            LogicalOrFunction -> create(node, BinaryOperator.OR)
        }
    }

    private fun literalFunction(node: PredefinedFunctionNode): Statement {
        val result = node.outputWireVectors()[0]
        val value = (node.predefinedFunction as LiteralFunction).value

        return Assignment(
            destReference = Reference(Identifier.wire(result)),
            expression = IntLiteral(value)
        )
    }

    private fun registerFunction(node: PredefinedFunctionNode): List<Statement> = buildList {
        val input = node.inputWireVectors()
        val output = node.outputWireVectors()

        data class RegisterWires(
            val input: String,
            val output: String,
            val regsiter: String,
            val size: Int,
        )

        val registers = input.zip(output).map { (input, output) ->
            RegisterWires(
                input = Identifier.wire(input),
                output = Identifier.wire(output),
                regsiter = "${Identifier.wire(input)}_register",
                size = input.wires.size,
            )
        }

        registers.forEach {
            add(
                Declaration(
                    name = it.regsiter,
                    type = DataType.REG,
                    startIndex = it.size - 1,
                    endIndex = 0,
                )
            )

            add(
                Assignment(
                    destReference = Reference(it.output),
                    expression = Reference(it.regsiter)
                )
            )

            add(
                Always(
                    sensitivity = Clocked("clock"),
                    statements = listOf(
                        IfStatement(
                            ifBranch = IfBranch(
                                condition = Reference("reset"),
                                then = listOf(
                                    NonBlockingAssignment(
                                        variableName = it.regsiter,
                                        expression = IntLiteral(0),
                                    )
                                ),
                            ),
                            elseIfBranches = listOf(),
                            elseStatements = listOf(
                                NonBlockingAssignment(
                                    variableName = it.regsiter,
                                    expression = Reference(it.input),
                                )
                            ),
                        )
                    )
                )
            )
        }
    }

    fun create(node: PredefinedFunctionNode): List<Statement> = buildList {
        addAll(Declarations.create(node))

        when (node.predefinedFunction) {
            is BinaryFunction -> add(binaryFunction(node))
            is LiteralFunction -> add(literalFunction(node))
            is RegisterFunction -> addAll(registerFunction(node))
        }
    }
}