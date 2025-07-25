package com.uabutler.netlistir.util

import com.uabutler.netlistir.builder.util.IntegerParameterValue
import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.*

sealed class PredefinedFunction(
    val inputs: List<IO>,
    val outputs: List<IO>,
) {

    data class IO(
        val name: String,
        val structure: InterfaceStructure,
    ) {
        fun toWireVectorGroup(
            parent: Node,
            wireType: WireType,
        ) = when (wireType) {
            WireType.INPUT -> toInputWireVectorGroup(parent)
            WireType.OUTPUT -> toOutputWireVectorGroup(parent)
        }

        fun toInputWireVectorGroup(parent: Node) = InputWireVectorGroup(
            identifier = name,
            parentNode = parent,
            structure = structure,
        )

        fun toOutputWireVectorGroup(parent: Node) = OutputWireVectorGroup(
            identifier = name,
            parentNode = parent,
            structure = structure,
        )
    }

    companion object {
        fun wireVector(size: Int) = VectorInterfaceStructure(WireInterfaceStructure, size)
        fun wire() = WireInterfaceStructure

        fun search(invocation: Module.Invocation): PredefinedFunction? {
            val size = invocation.parameters.firstOrNull()?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val value = invocation.parameters.getOrNull(1)?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val interfaceStructure = invocation.interfaces.firstOrNull()

            return when (invocation.gaplFunctionName) {
                "less_than_equals" -> LessThanEqualsFunction(size!!)
                "greater_than_equals" -> GreaterThanEqualsFunction(size!!)
                "equals" -> EqualsFunction(size!!)
                "not_equals" -> NotEqualsFunction(size!!)
                "and" -> LogicalAndFunction
                "or" -> LogicalOrFunction
                "bitwise_and" -> BitwiseAndFunction(size!!)
                "bitwise_or" -> BitwiseOrFunction(size!!)
                "bitwise_xor" -> BitwiseXorFunction(size!!)
                "add" -> AdditionFunction(size!!)
                "subtract" -> SubtractionFunction(size!!)
                "multiply" -> MultiplicationFunction(size!!)
                "left_shift" -> LeftShiftFunction(size!!)
                "right_shift" -> RightShiftFunction(size!!)
                "register" -> RegisterFunction(interfaceStructure!!)
                "literal" -> LiteralFunction(size!!, value!!)
                else -> null
            }
        }
    }

}

sealed class BinaryFunction(
    val lhs: InterfaceStructure,
    val rhs: InterfaceStructure,
    val result: InterfaceStructure,
): PredefinedFunction(
    inputs = listOf(IO("lhs", lhs), IO("rhs", rhs)),
    outputs = listOf(IO("result", result)),
)

sealed class BooleanComparison(
    open val size: Int,
): BinaryFunction(
    lhs = wireVector(size),
    rhs = wireVector(size),
    result = wire(),
)

data class EqualsFunction(
    override val size: Int,
): BooleanComparison(size)

data class NotEqualsFunction(
    override val size: Int,
): BooleanComparison(size)

data class LessThanEqualsFunction(
    override val size: Int,
): BooleanComparison(size)

data class GreaterThanEqualsFunction(
    override val size: Int,
): BooleanComparison(size)

sealed class BooleanFunction: BinaryFunction(
    lhs = wire(),
    rhs = wire(),
    result = wire(),
)

data object LogicalAndFunction: BooleanFunction()

data object LogicalOrFunction: BooleanFunction()

sealed class BinaryArithmeticFunction(
    open val size: Int,
): BinaryFunction(
    lhs = wireVector(size),
    rhs = wireVector(size),
    result = wireVector(size),
)

data class BitwiseAndFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class BitwiseOrFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class BitwiseXorFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class AdditionFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class SubtractionFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class MultiplicationFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class RightShiftFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class LeftShiftFunction(
    override val size: Int,
): BinaryArithmeticFunction(size)

data class RegisterFunction(
    val storageStructure: InterfaceStructure
): PredefinedFunction(
    inputs = listOf(IO("next", storageStructure)),
    outputs = listOf(IO("current", storageStructure)),
)

data class LiteralFunction(
    val size: Int,
    val value: Int,
): PredefinedFunction(
    inputs = listOf(),
    outputs = listOf(IO("value", wireVector(size))),
)
