package com.uabutler.gaplir.builder.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure
import com.uabutler.gaplir.util.InterfaceDescription
import com.uabutler.util.InterfaceType

sealed class PredefinedFunction(
    val inputs: List<InterfaceDescription>,
    val outputs: List<InterfaceDescription>,
) {
    companion object {
        fun wireVector(size: Int) = VectorInterfaceStructure(WireInterfaceStructure, size)
        fun wire() = WireInterfaceStructure

        fun searchPredefinedFunctions(instantiationData: ModuleInstantiationTracker.ModuleInstantiationData): PredefinedFunction? {
            val size = instantiationData.genericParameterValues.firstOrNull()?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val value = instantiationData.genericParameterValues.getOrNull(1)?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val interfaceStructure = instantiationData.genericInterfaceValues.firstOrNull()

            return when (instantiationData.functionIdentifier) {
                "less_than_equals" -> LessThanEqualsFunction(size!!)
                "greater_than_equals" -> GreaterThanEqualsFunction(size!!)
                "equals" -> EqualsFunction(size!!)
                "not_equals" -> NotEqualsFunction(size!!)
                "and" -> AndFunction()
                "or" -> OrFunction()
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
    inputs = listOf(
        InterfaceDescription(name = "lhs", interfaceStructure = lhs, interfaceType = InterfaceType.SIGNAL),
        InterfaceDescription(name = "rhs", interfaceStructure = rhs, interfaceType = InterfaceType.SIGNAL),
    ),
    outputs = listOf(
        InterfaceDescription(name = "result", interfaceStructure = result, interfaceType = InterfaceType.SIGNAL),
    ),
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

class AndFunction: BooleanFunction()

class OrFunction: BooleanFunction()

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
    inputs = listOf(
        InterfaceDescription(name = "next", interfaceStructure = storageStructure, interfaceType = InterfaceType.SIGNAL),
    ),
    outputs = listOf(
        InterfaceDescription(name = "current", interfaceStructure = storageStructure, interfaceType = InterfaceType.SIGNAL),
    ),
)

data class LiteralFunction(
    val size: Int,
    val value: Int,
    val storageStructure: InterfaceStructure = wireVector(size),
): PredefinedFunction(
    inputs = listOf(),
    outputs = listOf(InterfaceDescription(name = "value", interfaceStructure = storageStructure, interfaceType = InterfaceType.SIGNAL)),
)
