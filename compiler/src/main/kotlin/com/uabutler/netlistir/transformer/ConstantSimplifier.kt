package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.BinaryFunction
import com.uabutler.netlistir.util.BitwiseAndFunction
import com.uabutler.netlistir.util.BitwiseNotFunction
import com.uabutler.netlistir.util.BitwiseOrFunction
import com.uabutler.netlistir.util.BitwiseXorFunction
import com.uabutler.netlistir.util.DemuxFunction
import com.uabutler.netlistir.util.EqualsFunction
import com.uabutler.netlistir.util.GreaterThanEqualsFunction
import com.uabutler.netlistir.util.GreaterThanFunction
import com.uabutler.netlistir.util.IntegerRegisterFunction
import com.uabutler.netlistir.util.LeftShiftFunction
import com.uabutler.netlistir.util.LessThanEqualsFunction
import com.uabutler.netlistir.util.LessThanFunction
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.LogicalAndFunction
import com.uabutler.netlistir.util.LogicalNotFunction
import com.uabutler.netlistir.util.LogicalOrFunction
import com.uabutler.netlistir.util.MultiplicationFunction
import com.uabutler.netlistir.util.MuxFunction
import com.uabutler.netlistir.util.NotEqualsFunction
import com.uabutler.netlistir.util.PriorityFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.netlistir.util.RightShiftFunction
import com.uabutler.netlistir.util.SubtractionFunction
import com.uabutler.netlistir.util.UnaryFunction
import com.uabutler.util.Logger
import java.math.BigInteger

/**
 * Replaces every node whose inputs are all constants with the constant it evaluates to, repeating
 * until nothing is left to fold.
 *
 * ## Bit order
 *
 * Everything here is **LSB-first**: index 0 of a bit list is bit 0 of the value, matching
 * `OutputWire.index` and `BigInteger.testBit`. The previous implementation built LSB-first lists in
 * one helper and read them back MSB-first in another, so every folded multi-bit value came out
 * bit-reversed.
 *
 * ## Width
 *
 * Results are masked to the node's own output width, in two's complement. Folding used to format
 * the value as a binary string and pad it, which neither truncated an oversized result (a shift or
 * a multiply) nor handled a negative one (a subtraction produced a leading `-` and a corrupt list).
 *
 * ## Registers
 *
 * A register whose input is constant folds to that constant, which **changes behaviour for one
 * cycle after reset**: the emitted register resets to 0 and only then carries its value, whereas
 * the folded constant is correct immediately. That is deliberate - it is the more faithful reading
 * of a constant - but it is a real semantic change, and it is why this pass is opt-in.
 *
 * ## What is deliberately not folded
 *
 * A mux whose selector is out of range holds its previous value in the emitted Verilog (the case
 * statement has no default), so there is no constant to fold to and the node is left alone. Records
 * spread over several wire vectors are also left alone for mux/demux/priority rather than guessing
 * at the slice layout; only the single-vector shape is folded.
 */
object ConstantSimplifier: Transformer {

    private fun sourceLiteral(wire: InputWire): LiteralFunction? {
        val module = wire.parentWireVector.parentGroup.parentNode.parentModule
        val source = module.getConnectionForInputWire(wire).source
        val sourceNode = source.parentWireVector.parentGroup.parentNode
        if (sourceNode !is PredefinedFunctionNode) return null
        return sourceNode.predefinedFunction as? LiteralFunction
    }

    private fun allInputsConstant(node: BodyNode): Boolean {
        val wires = node.inputWires()
        if (wires.isEmpty()) return false
        return wires.all { sourceLiteral(it) != null }
    }

    /** LSB-first bits currently driving [wires], which must all be literal-driven. */
    private fun bitsOf(wires: List<InputWire>): List<Boolean> {
        val module = wires.first().parentWireVector.parentGroup.parentNode.parentModule
        return wires.map { wire ->
            val source = module.getConnectionForInputWire(wire).source
            val sourceNode = source.parentWireVector.parentGroup.parentNode as PredefinedFunctionNode
            (sourceNode.predefinedFunction as LiteralFunction).value.testBit(source.index)
        }
    }

    private fun valueOf(bits: List<Boolean>): BigInteger =
        bits.foldIndexed(BigInteger.ZERO) { index, acc, bit -> if (bit) acc.setBit(index) else acc }

    private fun bitsOf(value: BigInteger, size: Int): List<Boolean> =
        List(size) { value.testBit(it) }

    private fun boolBits(condition: Boolean, size: Int): List<Boolean> =
        bitsOf(if (condition) BigInteger.ONE else BigInteger.ZERO, size)

    private fun groupWires(node: PredefinedFunctionNode, identifier: String) =
        node.inputWireVectorGroups.first { it.identifier == identifier }.wires()

    /**
     * The constant this node evaluates to, LSB-first over [BodyNode.outputWires], or null when it
     * has no single constant value and must be left in place.
     */
    private fun fold(node: BodyNode): List<Boolean>? {
        val width = node.outputWires().size

        if (node is PassThroughNode) return bitsOf(node.inputWires())
        if (node !is PredefinedFunctionNode) return null

        return when (val function = node.predefinedFunction) {
            is LiteralFunction -> null
            is BinaryFunction -> {
                val lhs = valueOf(bitsOf(node.inputWireVectorGroups[0].wires()))
                val rhs = valueOf(bitsOf(node.inputWireVectorGroups[1].wires()))
                when (function) {
                    is AdditionFunction -> bitsOf(lhs + rhs, width)
                    is SubtractionFunction -> bitsOf(lhs - rhs, width)
                    is MultiplicationFunction -> bitsOf(lhs * rhs, width)
                    is BitwiseAndFunction -> bitsOf(lhs and rhs, width)
                    is BitwiseOrFunction -> bitsOf(lhs or rhs, width)
                    is BitwiseXorFunction -> bitsOf(lhs xor rhs, width)
                    // The shift amount is unsigned and can exceed any sane width; clamping keeps
                    // `shl` from allocating an enormous BigInteger for a result that is all zeros.
                    is LeftShiftFunction -> bitsOf(lhs shl rhs.min(width.toBigInteger()).toInt(), width)
                    is RightShiftFunction -> bitsOf(lhs shr rhs.min(width.toBigInteger()).toInt(), width)
                    LogicalAndFunction ->
                        boolBits(lhs.signum() != 0 && rhs.signum() != 0, width)
                    LogicalOrFunction ->
                        boolBits(lhs.signum() != 0 || rhs.signum() != 0, width)
                    // Both operands come from bit lists, so both are non-negative and these are the
                    // unsigned comparisons the emitted Verilog performs on plain `wire` vectors.
                    is EqualsFunction -> boolBits(lhs == rhs, width)
                    is NotEqualsFunction -> boolBits(lhs != rhs, width)
                    is LessThanFunction -> boolBits(lhs < rhs, width)
                    is LessThanEqualsFunction -> boolBits(lhs <= rhs, width)
                    is GreaterThanFunction -> boolBits(lhs > rhs, width)
                    is GreaterThanEqualsFunction -> boolBits(lhs >= rhs, width)
                }
            }
            is UnaryFunction -> {
                val input = bitsOf(node.inputWireVectorGroups[0].wires())
                when (function) {
                    is BitwiseNotFunction -> input.map { !it }.take(width)
                    is LogicalNotFunction -> boolBits(input.none { it }, width)
                }
            }
            is RegisterFunction, is IntegerRegisterFunction -> bitsOf(node.inputWireVectorGroups[0].wires())
            is MuxFunction -> {
                val selector = valueOf(bitsOf(groupWires(node, "selector")))
                val inputs = bitsOf(groupWires(node, "inputs"))
                // Out of range selects nothing: the emitted case statement has no default, so the
                // output latches its previous value rather than taking a constant.
                if (selector >= function.inputCount.toBigInteger()) return null
                val start = selector.toInt() * width
                if (start + width > inputs.size) return null
                inputs.subList(start, start + width)
            }
            is DemuxFunction -> {
                val selector = valueOf(bitsOf(groupWires(node, "selector")))
                val input = bitsOf(groupWires(node, "input"))
                // Every output is reset to zero first, so an out-of-range selector is all zeros.
                val bits = MutableList(width) { false }
                val start = selector.toInt() * input.size
                if (selector < function.outputCount.toBigInteger() && start + input.size <= width) {
                    input.forEachIndexed { offset, bit -> bits[start + offset] = bit }
                }
                bits
            }
            is PriorityFunction -> {
                val conditionals = node.inputWireVectorGroups.first { it.identifier == "conditionals" }
                val conditionVectors = conditionals.wireVectors.filter { it.identifier.first() == "condition" }
                val valueVectors = conditionals.wireVectors.filter { it.identifier.first() == "value" }
                // Records spread the value across several vectors; only the single-vector shape has
                // an unambiguous per-branch slice, so anything else is left unfolded.
                if (conditionVectors.size != 1 || valueVectors.size != 1) return null

                val conditions = bitsOf(conditionVectors.single().wires)
                val values = bitsOf(valueVectors.single().wires)
                if (values.size != width * function.conditionalCount) return null

                // First true condition wins, matching the emitted if / else-if chain.
                val taken = (0 until function.conditionalCount).firstOrNull { conditions.getOrElse(it) { false } }
                if (taken == null) bitsOf(groupWires(node, "default"))
                else values.subList(taken * width, (taken + 1) * width)
            }
        }
    }

    private var counter = 0

    private fun literalNodeFor(module: MutableModule, bits: List<Boolean>): PredefinedFunctionNode {
        val function = LiteralFunction(bits.size, valueOf(bits))
        return PredefinedFunctionNode(
            identifier = "constant\$${counter++}",
            parentModule = module,
            inputWireVectorGroupsBuilder = { node -> function.inputs.map { it.toInputWireVectorGroup(node) } },
            outputWireVectorGroupsBuilder = { node -> function.outputs.map { it.toOutputWireVectorGroup(node) } },
            predefinedFunction = function,
        ).also { module.addBodyNode(it) }
    }

    private fun swapIn(module: MutableModule, original: BodyNode, replacement: PredefinedFunctionNode) {
        original.outputWires().zip(replacement.outputWires()).forEach { (from, to) ->
            // Snapshot: the loop rewires the very connections it is walking.
            module.getConnectionsForOutputWire(from).toList().forEach { connection ->
                module.disconnect(connection.sink)
                module.connect(connection.sink, to)
            }
        }
    }

    private fun simplify(original: Module): Module {
        val module = original.toMutableModule()
        var folded = 0

        while (true) {
            val batch = module.getBodyNodes()
                .filter { it !is PredefinedFunctionNode || it.predefinedFunction !is LiteralFunction }
                .filter { allInputsConstant(it) }
                .mapNotNull { node -> fold(node)?.let { node to it } }
            if (batch.isEmpty()) break

            batch.forEach { (node, bits) ->
                swapIn(module, node, literalNodeFor(module, bits))
                node.inputWires().forEach { module.disconnect(it) }
                module.removeNode(node)
            }
            folded += batch.size
        }

        if (folded > 0) {
            Logger.debug { "Constant-folded $folded node(s) in ${original.invocation.gaplFunctionName}" }
        }
        return module
    }

    override fun transform(original: List<Module>): List<Module> = original.map { simplify(it) }
}
