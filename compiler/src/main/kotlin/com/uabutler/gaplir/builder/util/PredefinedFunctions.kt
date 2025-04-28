package com.uabutler.gaplir.builder.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure

sealed class PredefinedFunction(
    val inputs: Map<String, InterfaceStructure>,
    val outputs: Map<String, InterfaceStructure>,
) {
    companion object {
        fun wire(size: Int) = VectorInterfaceStructure(WireInterfaceStructure, size)

        fun searchPredefinedFunctions(instantiationData: ModuleInstantiationTracker.ModuleInstantiationData): PredefinedFunction? {
            val size = instantiationData.genericParameterValues.firstOrNull()?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val value = instantiationData.genericParameterValues.getOrNull(1)?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val interfaceStructure = instantiationData.genericInterfaceValues.firstOrNull()

            return when (instantiationData.functionIdentifier) {
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

sealed class BinaryOperationFunction(
    open val size: Int,
    val lhs: InterfaceStructure = wire(size),
    val rhs: InterfaceStructure = wire(size),
    val result: InterfaceStructure = wire(size),
): PredefinedFunction(
    inputs = mapOf("lhs" to lhs, "rhs" to rhs),
    outputs = mapOf("result" to result),
)

data class AdditionFunction(
    override val size: Int,
): BinaryOperationFunction(size)

data class SubtractionFunction(
    override val size: Int,
): BinaryOperationFunction(size)

data class MultiplicationFunction(
    override val size: Int,
): BinaryOperationFunction(size)

data class RightShiftFunction(
    override val size: Int,
): BinaryOperationFunction(size)

data class LeftShiftFunction(
    override val size: Int,
): BinaryOperationFunction(size)

data class RegisterFunction(
    val storageStructure: InterfaceStructure
): PredefinedFunction(
    inputs = mapOf("next" to storageStructure),
    outputs = mapOf("current" to storageStructure),
)

data class LiteralFunction(
    val size: Int,
    val value: Int,
    val storageStructure: InterfaceStructure = wire(size),
): PredefinedFunction(
    inputs = mapOf(),
    outputs = mapOf("value" to storageStructure),
)
