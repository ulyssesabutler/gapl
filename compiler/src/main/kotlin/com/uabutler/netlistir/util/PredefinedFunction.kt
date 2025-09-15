package com.uabutler.netlistir.util

import com.uabutler.netlistir.builder.util.IntegerParameterValue
import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.RecordInterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.*
import com.uabutler.util.PredefinedFunctionNames

sealed class PredefinedFunction(
    val inputs: List<IO>,
    val outputs: List<IO>,
) {

    data class IO(
        val name: String,
        val structure: InterfaceStructure,
    ) {
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
        fun vector(structure: InterfaceStructure, size: Int) = VectorInterfaceStructure(structure, size)
        fun conditionals(conditionalCount: Int, inputStructure: InterfaceStructure) = vector(conditional(inputStructure), conditionalCount)
        fun conditional(inputStructure: InterfaceStructure) = RecordInterfaceStructure(
            ports = mapOf(
                "condition" to WireInterfaceStructure,
                "value" to inputStructure,
            ),
        )

        fun search(invocation: Module.Invocation): PredefinedFunction? {
            val size = invocation.parameters.firstOrNull()?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val value = invocation.parameters.getOrNull(1)?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val interfaceStructure = invocation.interfaces.firstOrNull()

            return when (PredefinedFunctionNames.from(invocation.gaplFunctionName)) {
                PredefinedFunctionNames.LESS_THAN_EQUALS -> LessThanEqualsFunction(size!!)
                PredefinedFunctionNames.GREATER_THAN_EQUALS -> GreaterThanEqualsFunction(size!!)
                PredefinedFunctionNames.EQUALS -> EqualsFunction(size!!)
                PredefinedFunctionNames.NOT_EQUALS -> NotEqualsFunction(size!!)
                PredefinedFunctionNames.AND -> LogicalAndFunction
                PredefinedFunctionNames.OR -> LogicalOrFunction
                PredefinedFunctionNames.BITWISE_AND -> BitwiseAndFunction(size!!)
                PredefinedFunctionNames.BITWISE_OR -> BitwiseOrFunction(size!!)
                PredefinedFunctionNames.BITWISE_XOR -> BitwiseXorFunction(size!!)
                PredefinedFunctionNames.ADD -> AdditionFunction(size!!)
                PredefinedFunctionNames.SUBTRACT -> SubtractionFunction(size!!)
                PredefinedFunctionNames.MULTIPLY -> MultiplicationFunction(size!!)
                PredefinedFunctionNames.LEFT_SHIFT -> LeftShiftFunction(size!!)
                PredefinedFunctionNames.RIGHT_SHIFT -> RightShiftFunction(size!!)
                PredefinedFunctionNames.REGISTER -> RegisterFunction(interfaceStructure!!)
                PredefinedFunctionNames.LITERAL -> LiteralFunction(size!!, value!!)
                PredefinedFunctionNames.MUX -> {
                    val outputStructure = invocation.interfaces[0]
                    val inputCount = (invocation.parameters[0] as IntegerParameterValue).value
                    val selectorSize = (invocation.parameters[1] as IntegerParameterValue).value

                    MuxFunction(outputStructure, inputCount, selectorSize)
                }
                PredefinedFunctionNames.DEMUX -> {
                    val inputStructure = invocation.interfaces[0]
                    val outputCount = (invocation.parameters[0] as IntegerParameterValue).value
                    val selectorSize = (invocation.parameters[1] as IntegerParameterValue).value

                    DemuxFunction(inputStructure, outputCount, selectorSize)
                }
                PredefinedFunctionNames.PRIORITY -> {
                    val conditionalCount = (invocation.parameters[0] as IntegerParameterValue).value
                    val inputStructure = invocation.interfaces[0]

                    PriorityFunction(conditionalCount, inputStructure)
                }
                null -> null
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
    val storageStructure: InterfaceStructure,
): PredefinedFunction(
    inputs = listOf(IO("next", storageStructure)),
    outputs = listOf(IO("current", storageStructure)),
)

data class MuxFunction(
    val outputStructure: InterfaceStructure,
    val inputCount: Int,
    val selectorSize: Int,
): PredefinedFunction(
    inputs = listOf(IO("selector", wireVector(selectorSize)), IO("inputs", vector(outputStructure, inputCount))),
    outputs = listOf(IO("output", outputStructure)),
)

data class DemuxFunction(
    val inputStructure: InterfaceStructure,
    val outputCount: Int,
    val selectorSize: Int,
): PredefinedFunction(
    inputs = listOf(IO("selector", wireVector(selectorSize)), IO("input", inputStructure)),
    outputs = listOf(IO("outputs", vector(inputStructure, outputCount))),
)

data class PriorityFunction(
    val conditionalCount: Int,
    val inputStructure: InterfaceStructure,
): PredefinedFunction(
    inputs = listOf(IO("conditionals", conditionals(conditionalCount, inputStructure)), IO("default", inputStructure)),
    outputs = listOf(IO("output", inputStructure)),
)

data class LiteralFunction(
    val size: Int,
    val value: Int,
): PredefinedFunction(
    inputs = listOf(),
    outputs = listOf(IO("value", wireVector(size))),
)
