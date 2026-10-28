package com.uabutler.netlistir.util

import com.uabutler.netlistir.builder.util.IntegerParameterValue
import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.RecordInterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.*
import com.uabutler.util.PredefinedFunctionNames
import java.math.BigInteger

sealed class PredefinedFunction(
    val inputs: List<IO>,
    val outputs: List<IO>,
) {

    /**
     * Which outputs each input reaches combinationally, by IO index - or null, meaning every input
     * reaches every output, which is true of every pure operator. The handshake primitives at the end of
     * this file override it: they carry `valid` forward and `ready` back through one node, and hold state,
     * so treating them as all-to-all would invent combinational loops between connected islands.
     */
    open val combinationalPaths: Map<Int, Set<Int>>? get() = null

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
                if (it is IntegerParameterValue) it.value.intValueExact() else null
            }

            val value = invocation.parameters.getOrNull(1)?.let {
                if (it is IntegerParameterValue) it.value else null
            }

            val interfaceStructure = invocation.interfaces.firstOrNull()

            return when (PredefinedFunctionNames.from(invocation.gaplFunctionName)) {
                PredefinedFunctionNames.LESS_THAN_EQUALS -> LessThanEqualsFunction(size!!)
                PredefinedFunctionNames.GREATER_THAN_EQUALS -> GreaterThanEqualsFunction(size!!)
                PredefinedFunctionNames.LESS_THAN -> LessThanFunction(size!!)
                PredefinedFunctionNames.GREATER_THAN -> GreaterThanFunction(size!!)
                PredefinedFunctionNames.EQUALS -> EqualsFunction(size!!)
                PredefinedFunctionNames.NOT_EQUALS -> NotEqualsFunction(size!!)
                PredefinedFunctionNames.AND -> LogicalAndFunction
                PredefinedFunctionNames.OR -> LogicalOrFunction
                PredefinedFunctionNames.NOT -> LogicalNotFunction
                PredefinedFunctionNames.BITWISE_AND -> BitwiseAndFunction(size!!)
                PredefinedFunctionNames.BITWISE_OR -> BitwiseOrFunction(size!!)
                PredefinedFunctionNames.BITWISE_XOR -> BitwiseXorFunction(size!!)
                PredefinedFunctionNames.BITWISE_NOT -> BitwiseNotFunction(size!!)
                PredefinedFunctionNames.ADD -> AdditionFunction(size!!)
                PredefinedFunctionNames.SUBTRACT -> SubtractionFunction(size!!)
                PredefinedFunctionNames.MULTIPLY -> MultiplicationFunction(size!!)
                PredefinedFunctionNames.LEFT_SHIFT -> LeftShiftFunction(size!!)
                PredefinedFunctionNames.RIGHT_SHIFT -> RightShiftFunction(size!!)
                PredefinedFunctionNames.REGISTER -> RegisterFunction(interfaceStructure!!)
                PredefinedFunctionNames.INTEGER_REGISTER -> {
                    val size = (invocation.parameters[0] as IntegerParameterValue).value.intValueExact()
                    val default = (invocation.parameters[1] as IntegerParameterValue).value

                    IntegerRegisterFunction(size, default)
                }
                PredefinedFunctionNames.LITERAL -> LiteralFunction(size!!, value!!)
                PredefinedFunctionNames.MUX -> {
                    val outputStructure = invocation.interfaces[0]
                    val inputCount = (invocation.parameters[0] as IntegerParameterValue).value.intValueExact()
                    val selectorSize = (invocation.parameters[1] as IntegerParameterValue).value.intValueExact()

                    MuxFunction(outputStructure, inputCount, selectorSize)
                }
                PredefinedFunctionNames.DEMUX -> {
                    val inputStructure = invocation.interfaces[0]
                    val outputCount = (invocation.parameters[0] as IntegerParameterValue).value.intValueExact()
                    val selectorSize = (invocation.parameters[1] as IntegerParameterValue).value.intValueExact()

                    DemuxFunction(inputStructure, outputCount, selectorSize)
                }
                PredefinedFunctionNames.PRIORITY -> {
                    val conditionalCount = (invocation.parameters[0] as IntegerParameterValue).value.intValueExact()
                    val inputStructure = invocation.interfaces[0]

                    PriorityFunction(conditionalCount, inputStructure)
                }
                PredefinedFunctionNames.PROJECT, PredefinedFunctionNames.OVERLAY, PredefinedFunctionNames.ZEROS,
                PredefinedFunctionNames.FORK_EAGER, PredefinedFunctionNames.FORK_LAZY ->
                    throw IllegalStateException(
                        "${invocation.gaplFunctionName} is lowered to wiring by NodeBuilder and should never reach PredefinedFunction.search - this is a compiler bug"
                    )
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

sealed class UnaryFunction(
    val input: InterfaceStructure,
    val result: InterfaceStructure,
): PredefinedFunction(
    inputs = listOf(IO("input", input)),
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

data class LessThanFunction(
    override val size: Int,
): BooleanComparison(size)

data class GreaterThanFunction(
    override val size: Int,
): BooleanComparison(size)

sealed class BooleanBinaryFunction: BinaryFunction(
    lhs = wire(),
    rhs = wire(),
    result = wire(),
)

sealed class BooleanUnaryFunction: UnaryFunction(
    input = wire(),
    result = wire(),
)

data object LogicalAndFunction: BooleanBinaryFunction()

data object LogicalOrFunction: BooleanBinaryFunction()

data object LogicalNotFunction: BooleanUnaryFunction()

sealed class BinaryArithmeticFunction(
    open val size: Int,
): BinaryFunction(
    lhs = wireVector(size),
    rhs = wireVector(size),
    result = wireVector(size),
)

sealed class UnaryArithmeticFunction(
    open val size: Int,
): UnaryFunction(
    input = wireVector(size),
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

data class BitwiseNotFunction(
    override val size: Int,
): UnaryArithmeticFunction(size)

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

data class IntegerRegisterFunction(
    val size: Int,
    val default: BigInteger,
): PredefinedFunction(
    inputs = listOf(IO("next", wireVector(size))),
    outputs = listOf(IO("current", wireVector(size))),
)

val PredefinedFunction.isRegister: Boolean
    get() = this is RegisterFunction || this is IntegerRegisterFunction

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
    val value: BigInteger,
): PredefinedFunction(
    inputs = listOf(),
    outputs = listOf(IO("value", wireVector(size))),
)

/*
 * Layer-3 handshake plumbing (type-system.md §5, "the plumbing is first-class compiler primitives,
 * lowered late"). These are never written by users: NodeBuilder inserts them at islands, they stay
 * first-class through the transformer pipeline (so retiming cannot mistake them for datapath), and
 * HandshakeLowering expands them into ordinary gates and registers just before Verilog emission and
 * when simengine loads a design - so neither of those ever sees one.
 */

/**
 * An island's `valid`: its input `valid`, delayed by the island's latency. Before retiming every island
 * is combinational, so `depth` is 0 (a wire); retiming will set it (stage 2d).
 */
data class ValidDelayFunction(
    val depth: Int,
): PredefinedFunction(
    inputs = listOf(IO("valid_in", wire())),
    outputs = listOf(IO("valid_out", wire())),
) {
    // A delayed valid passes through registers, so only depth 0 is a combinational path.
    override val combinationalPaths get() = if (depth == 0) mapOf(0 to setOf(0)) else emptyMap()
}

/**
 * A @ready_valid island's control (type-system.md §5, "2c design"), for an island over a @pure callee
 * (coercion B), over a @valid callee (coercion C, whose own output valids arrive as `produced`), and - as
 * the identity island with one input - fork_eager. Inputs, in order: `in_valid` per input channel,
 * `out_ready` per output channel, then (C only) `produced` per output. Outputs: `in_ready` per input, then
 * `out_valid` per output. Holds one done bit per output (none with a single output), recording which
 * outputs already took the current item; all inputs are consumed together once every produced output has.
 */
data class IslandControlFunction(
    val inputCount: Int,
    val outputCount: Int,
    val producedByCallee: Boolean,
): PredefinedFunction(
    inputs = List(inputCount) { IO("in_valid_$it", wire()) } +
        List(outputCount) { IO("out_ready_$it", wire()) } +
        (if (producedByCallee) List(outputCount) { IO("produced_$it", wire()) } else emptyList()),
    outputs = List(inputCount) { IO("in_ready_$it", wire()) } + List(outputCount) { IO("out_valid_$it", wire()) },
) {
    fun inValid(i: Int) = i
    fun outReady(o: Int) = inputCount + o
    fun produced(o: Int) = inputCount + outputCount + o
    fun inReady(i: Int) = i
    fun outValid(o: Int) = inputCount + o

    // Never out_ready -> out_valid: an output's valid depends on ready only through its done bit.
    override val combinationalPaths: Map<Int, Set<Int>> get() {
        val allInReady = (0 until inputCount).map(::inReady).toSet()
        val allOutValid = (0 until outputCount).map(::outValid).toSet()
        return buildMap {
            (0 until inputCount).forEach { put(inValid(it), allInReady + if (producedByCallee) emptySet() else allOutValid) }
            (0 until outputCount).forEach { put(outReady(it), allInReady) }
            if (producedByCallee) (0 until outputCount).forEach { put(produced(it), allInReady + outValid(it)) }
        }
    }
}

/**
 * fork_lazy: offers the item to every branch only once all of them are ready. Inputs: `in_valid`, then
 * `out_ready` per branch. Outputs: `in_ready`, then `out_valid` per branch. No state - and its valids
 * depend on the other branches' readys, so a lazy fork whose branches rejoin is a real combinational loop.
 */
data class LazyForkFunction(
    val branchCount: Int,
): PredefinedFunction(
    inputs = listOf(IO("in_valid", wire())) + List(branchCount) { IO("out_ready_$it", wire()) },
    outputs = listOf(IO("in_ready", wire())) + List(branchCount) { IO("out_valid_$it", wire()) },
) {
    override val combinationalPaths: Map<Int, Set<Int>> get() = buildMap {
        put(0, (1..branchCount).toSet())
        (0 until branchCount).forEach { j -> put(1 + j, setOf(0) + (0 until branchCount).filter { it != j }.map { 1 + it }) }
    }
}
