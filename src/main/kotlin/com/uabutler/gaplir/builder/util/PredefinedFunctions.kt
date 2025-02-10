package com.uabutler.gaplir.builder.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure

sealed class PredefinedFunction(
    val inputs: Map<String, InterfaceStructure>,
    val outputs: Map<String, InterfaceStructure>,
) {
    companion object {
        private fun wire32bit() = VectorInterfaceStructure(WireInterfaceStructure, 32)

        fun searchPredefinedFunctions(instantiationData: ModuleInstantiationTracker.ModuleInstantiationData): PredefinedFunction? {
            return when (instantiationData.functionIdentifier) {
                "add" -> AdditionFunction(wire32bit(), wire32bit(), wire32bit())
                "subtract" -> SubtractionFunction(wire32bit(), wire32bit(), wire32bit())
                "multiply" -> MultiplicationFunction(wire32bit(), wire32bit(), wire32bit())
                "left_shift" -> LeftShiftFunction(wire32bit(), wire32bit(), wire32bit())
                "right_shift" -> RightShiftFunction(wire32bit(), wire32bit(), wire32bit())
                else -> null
            }
        }
    }
}

sealed class BinaryOperationFunction(
    open val lhs: InterfaceStructure,
    open val rhs: InterfaceStructure,
    open val result: InterfaceStructure
): PredefinedFunction(
    inputs = mapOf("lhs" to lhs, "rhs" to rhs),
    outputs = mapOf("result" to result),
)

data class AdditionFunction(
    override val lhs: VectorInterfaceStructure,
    override val rhs: VectorInterfaceStructure,
    override val result: VectorInterfaceStructure,
): BinaryOperationFunction(lhs, rhs, result)

data class SubtractionFunction(
    override val lhs: VectorInterfaceStructure,
    override val rhs: VectorInterfaceStructure,
    override val result: VectorInterfaceStructure,
): BinaryOperationFunction(lhs, rhs, result)

data class MultiplicationFunction(
    override val lhs: VectorInterfaceStructure,
    override val rhs: VectorInterfaceStructure,
    override val result: VectorInterfaceStructure,
): BinaryOperationFunction(lhs, rhs, result)

data class RightShiftFunction(
    override val lhs: VectorInterfaceStructure,
    override val rhs: VectorInterfaceStructure,
    override val result: VectorInterfaceStructure,
): BinaryOperationFunction(lhs, rhs, result)

data class LeftShiftFunction(
    override val lhs: VectorInterfaceStructure,
    override val rhs: VectorInterfaceStructure,
    override val result: VectorInterfaceStructure,
): BinaryOperationFunction(lhs, rhs, result)
