package com.uabutler.verilogir.builder.creator

import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.BinaryFunction
import com.uabutler.netlistir.util.BitwiseAndFunction
import com.uabutler.netlistir.util.BitwiseOrFunction
import com.uabutler.netlistir.util.BitwiseXorFunction
import com.uabutler.netlistir.util.DemuxFunction
import com.uabutler.netlistir.util.EqualsFunction
import com.uabutler.netlistir.util.GreaterThanEqualsFunction
import com.uabutler.netlistir.util.LeftShiftFunction
import com.uabutler.netlistir.util.LessThanEqualsFunction
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.LogicalAndFunction
import com.uabutler.netlistir.util.LogicalOrFunction
import com.uabutler.netlistir.util.MultiplicationFunction
import com.uabutler.netlistir.util.MuxFunction
import com.uabutler.netlistir.util.NotEqualsFunction
import com.uabutler.netlistir.util.PriorityFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.netlistir.util.RightShiftFunction
import com.uabutler.netlistir.util.SubtractionFunction
import com.uabutler.verilogir.builder.creator.util.Declarations
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.statement.Always
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.always.BlockingAssignment
import com.uabutler.verilogir.module.statement.always.CaseEntry
import com.uabutler.verilogir.module.statement.always.CaseStatement
import com.uabutler.verilogir.module.statement.always.Clocked
import com.uabutler.verilogir.module.statement.always.Combinational
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
            val register: String,
            val size: Int,
        )

        val registers = input.zip(output).map { (input, output) ->
            RegisterWires(
                input = Identifier.wire(input),
                output = Identifier.wire(output),
                register = "${Identifier.wire(input)}_register",
                size = input.wires.size,
            )
        }

        registers.forEach {
            add(
                Declaration(
                    name = it.register,
                    type = DataType.REG,
                    startIndex = it.size - 1,
                    endIndex = 0,
                )
            )

            add(
                Assignment(
                    destReference = Reference(it.output),
                    expression = Reference(it.register)
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
                                        variableName = Reference(it.register),
                                        expression = IntLiteral(0),
                                    )
                                ),
                            ),
                            elseIfBranches = listOf(),
                            elseStatements = listOf(
                                NonBlockingAssignment(
                                    variableName = Reference(it.register),
                                    expression = Reference(it.input),
                                )
                            ),
                        )
                    )
                )
            )
        }
    }

    fun muxFunction(node: PredefinedFunctionNode): List<Statement> {
        val selector = node.inputWireVectorGroups.first { it.identifier == "selector" }
        val inputs = node.inputWireVectorGroups.first { it.identifier == "inputs" }
        val output = node.outputWireVectorGroups.first { it.identifier == "output" }

        data class RegisterWires(
            val output: String,
            val register: String,
            val size: Int,
        )

        val registers = output.wireVectors.map { output ->
            RegisterWires(
                output = Identifier.wire(output),
                register = "${Identifier.wire(output)}_register",
                size = output.wires.size,
            )
        }

        val registerDeclarations = registers.map {
            Declaration(
                name = it.register,
                type = DataType.REG,
                startIndex = it.size - 1,
                endIndex = 0,
            )
        }

        val registerAssignments = registers.map {
            Assignment(
                destReference = Reference(it.output),
                expression = Reference(it.register)
            )
        }

        val inputCount = (node.predefinedFunction as MuxFunction).inputCount

        val entries = List(inputCount) { index ->
            val assignments = inputs.wireVectors.zip(registers).map { (inputs, outputRegister) ->
                val inputIdentifier = Identifier.wire(inputs)
                val outputIdentifier = outputRegister.register

                val inputSize = inputs.wires.size / inputCount
                val outputSize = outputRegister.size

                if (inputSize != outputSize) {
                    throw Exception("Compiler Bug: Invalid input and output sizes for mux function: $inputSize != $outputSize")
                }

                val startIndex = index * inputSize
                val endIndex = startIndex + inputSize - 1

                BlockingAssignment(
                    variableName = Reference(outputIdentifier),
                    expression = Reference(
                        variableName = inputIdentifier,
                        startIndex = endIndex,
                        endIndex = startIndex,
                    )
                )
            }

            CaseEntry(
                condition = IntLiteral(index),
                statements = assignments,
            )
        }

        val caseStatement = CaseStatement(
            selector = Reference(Identifier.wire(selector.wireVectors.first())),
            caseEntries = entries,
        )

        val alwaysStatement = Always(
            sensitivity = Combinational,
            statements = listOf(caseStatement),
        )

        return registerDeclarations + registerAssignments + alwaysStatement
    }

    fun demuxFunction(node: PredefinedFunctionNode): List<Statement> {
        val selector = node.inputWireVectorGroups.first { it.identifier == "selector" }
        val input = node.inputWireVectorGroups.first { it.identifier == "input" }
        val outputs = node.outputWireVectorGroups.first { it.identifier == "outputs" }

        data class RegisterWires(
            val output: String,
            val register: String,
            val size: Int,
        )

        val registers = outputs.wireVectors.map { output ->
            RegisterWires(
                output = Identifier.wire(output),
                register = "${Identifier.wire(output)}_register",
                size = output.wires.size,
            )
        }

        val registerDeclarations = registers.map {
            Declaration(
                name = it.register,
                type = DataType.REG,
                startIndex = it.size - 1,
                endIndex = 0,
            )
        }

        val registerAssignments = registers.map {
            Assignment(
                destReference = Reference(it.output),
                expression = Reference(it.register)
            )
        }

        val outputReset = registers.map {
            BlockingAssignment(
                variableName = Reference(it.register),
                expression = IntLiteral(0)
            )
        }

        val outputCount = (node.predefinedFunction as DemuxFunction).outputCount

        val entries = List(outputCount) { index ->
            val assignments = registers.zip(input.wireVectors).map { (outputsRegister, input) ->
                val outputIdentifier = outputsRegister.register
                val inputIdentifier = Identifier.wire(input)

                val inputSize = input.wires.size
                val outputSize = outputsRegister.size / outputCount

                if (inputSize != outputSize) {
                    throw Exception("Compiler Bug: Invalid input and output sizes for demux function: $inputSize != $outputSize")
                }

                val startIndex = index * outputSize
                val endIndex = startIndex + outputSize - 1

                BlockingAssignment(
                    variableName = Reference(
                        variableName = outputIdentifier,
                        startIndex = endIndex,
                        endIndex = startIndex,
                    ),
                    expression = Reference(inputIdentifier)
                )
            }

            CaseEntry(
                condition = IntLiteral(index),
                statements = assignments,
            )
        }

        val caseStatement = CaseStatement(
            selector = Reference(Identifier.wire(selector.wireVectors.first())),
            caseEntries = entries,
        )

        val alwaysStatement = Always(
            sensitivity = Combinational,
            statements = outputReset + caseStatement,
        )

        return registerDeclarations + registerAssignments + alwaysStatement
    }

    fun priorityFunction(node: PredefinedFunctionNode): List<Statement> {
        val conditionals = node.inputWireVectorGroups.first { it.identifier == "conditionals" }
        val default = node.inputWireVectorGroups.first { it.identifier == "default" }
        val output = node.outputWireVectorGroups.first { it.identifier == "output" }

        data class RegisterWires(
            val output: String,
            val register: String,
            val size: Int,
        )

        val registers = output.wireVectors.map { output ->
            RegisterWires(
                output = Identifier.wire(output),
                register = "${Identifier.wire(output)}_register",
                size = output.wires.size,
            )
        }

        val registerDeclarations = registers.map {
            Declaration(
                name = it.register,
                type = DataType.REG,
                startIndex = it.size - 1,
                endIndex = 0,
            )
        }

        val registerAssignments = registers.map {
            Assignment(
                destReference = Reference(it.output),
                expression = Reference(it.register)
            )
        }

        val inputCount = (node.predefinedFunction as PriorityFunction).conditionalCount

        val branches = List(inputCount) { index ->
            val conditions = conditionals.wireVectors.first { it.identifier.first() == "condition" }
            val values = conditionals.wireVectors.filter { it.identifier.first() == "value" }

            val assignments = values.zip(registers).map { (value, outputRegister) ->
                val inputIdentifier = Identifier.wire(value)
                val outputIdentifier = outputRegister.register

                val inputSize = value.wires.size / inputCount
                val outputSize = outputRegister.size

                if (inputSize != outputSize) {
                    throw Exception("Compiler Bug: Invalid input and output sizes for priority function: $inputSize != $outputSize")
                }

                val startIndex = index * inputSize
                val endIndex = startIndex + inputSize - 1

                BlockingAssignment(
                    variableName = Reference(outputIdentifier),
                    expression = Reference(
                        variableName = inputIdentifier,
                        startIndex = endIndex,
                        endIndex = startIndex,
                    )
                )
            }

            IfBranch(
                condition = Reference(Identifier.wire(conditions), index, index),
                then = assignments,
            )
        }

        val elseStatements = default.wireVectors.zip(registers).map { (value, outputRegister) ->
            val inputIdentifier = Identifier.wire(value)
            val outputIdentifier = outputRegister.register

            BlockingAssignment(
                variableName = Reference(outputIdentifier),
                expression = Reference(inputIdentifier)
            )
        }

        val ifStatement = IfStatement(
            ifBranch = branches.first(),
            elseIfBranches = branches.drop(1),
            elseStatements = elseStatements,
        )

        val alwaysStatement = Always(
            sensitivity = Combinational,
            statements = listOf(ifStatement),
        )

        return registerDeclarations + registerAssignments + alwaysStatement
    }

    fun create(node: PredefinedFunctionNode): List<Statement> = buildList {
        addAll(Declarations.create(node))

        when (node.predefinedFunction) {
            is BinaryFunction -> add(binaryFunction(node))
            is LiteralFunction -> add(literalFunction(node))
            is RegisterFunction -> addAll(registerFunction(node))
            is MuxFunction -> addAll(muxFunction(node))
            is DemuxFunction -> addAll(demuxFunction(node))
            is PriorityFunction -> addAll(priorityFunction(node))
        }
    }
}