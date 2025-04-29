package com.uabutler.verilogir.builder.node

import com.uabutler.gaplir.builder.util.*
import com.uabutler.gaplir.node.PredefinedFunctionInvocationNode
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.BinaryOperation
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.module.statement.util.BinaryOperator

object BinaryOperationConnector {

    private fun getOperation(function: BinaryFunction): BinaryOperator {
        return when (function) {
            is EqualsFunction -> BinaryOperator.EQUALS
            is NotEqualsFunction -> BinaryOperator.NOT_EQUALS
            is AndFunction -> BinaryOperator.BITWISE_AND
            is OrFunction -> BinaryOperator.BITWISE_OR
            is AdditionFunction -> BinaryOperator.ADD
            is SubtractionFunction -> BinaryOperator.SUBTRACT
            is MultiplicationFunction -> BinaryOperator.MULTIPLY
            is RightShiftFunction -> BinaryOperator.RIGHT_SHIFT
            is LeftShiftFunction -> BinaryOperator.LEFT_SHIFT
        }
    }

    fun connect(predefinedFunction: PredefinedFunctionInvocationNode, function: BinaryFunction): List<Statement> {
        val lhs = VerilogInterface.fromGAPLInterfaceStructure("${predefinedFunction.invocationName}_lhs_input", function.lhs)
        val rhs = VerilogInterface.fromGAPLInterfaceStructure("${predefinedFunction.invocationName}_rhs_input", function.rhs)
        val result = VerilogInterface.fromGAPLInterfaceStructure("${predefinedFunction.invocationName}_result_output", function.result)

        // TODO: Generalize
        assert(lhs.size == 1)
        assert(rhs.size == 1)
        assert(result.size == 1)

        val lhsWire = lhs.first()
        val rhsWire = rhs.first()
        val resultWire = result.first()

        return listOf(
            Assignment(
                destReference = Reference(
                    variableName = resultWire.name,
                    startIndex = resultWire.width - 1,
                    endIndex = 0,
                ),
                expression = BinaryOperation(
                    lhs = Reference(
                        variableName = lhsWire.name,
                        startIndex = lhsWire.width - 1,
                        endIndex = 0,
                    ),
                    rhs = Reference(
                        variableName = rhsWire.name,
                        startIndex = rhsWire.width - 1,
                        endIndex = 0,
                    ),
                    operator = getOperation(function)
                )
            )
        )
    }

}