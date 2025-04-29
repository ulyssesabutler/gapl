package com.uabutler.gaplir.builder.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure

sealed class PredefinedFunction(
    val inputs: Map<String, InterfaceStructure>,
    val outputs: Map<String, InterfaceStructure>,
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
                "equals" -> EqualsFunction(size!!)
                "not_equals" -> NotEqualsFunction(size!!)
                "and" -> AndFunction()
                "or" -> OrFunction()
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
    inputs = mapOf("lhs" to lhs, "rhs" to rhs),
    outputs = mapOf("result" to result),
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
    inputs = mapOf("next" to storageStructure),
    outputs = mapOf("current" to storageStructure),
)

data class LiteralFunction(
    val size: Int,
    val value: Int,
    val storageStructure: InterfaceStructure = wireVector(size),
): PredefinedFunction(
    inputs = mapOf(),
    outputs = mapOf("value" to storageStructure),
)
