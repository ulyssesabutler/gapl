package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.OutputWire
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
import java.util.IdentityHashMap

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
 * ## User-defined functions
 *
 * A [ModuleInvocationNode] whose inputs are all constant folds too: the callee is *evaluated* with
 * those inputs - the same folding rules run over its body against a value environment - and if
 * every one of its output ports resolves, the whole call collapses to a literal. This only matters
 * away from `--flatten all`, since full flattening inlines every invocation before this pass runs.
 * Evaluation is depth-limited, so a recursive call gives up rather than looping.
 *
 * ## What is deliberately not folded
 *
 * A mux whose selector is out of range holds its previous value in the emitted Verilog (the case
 * statement has no default), so there is no constant to fold to and the node is left alone. Records
 * spread over several wire vectors are also left alone for mux/demux/priority rather than guessing
 * at the slice layout; only the single-vector shape is folded.
 */
object ConstantSimplifier: Transformer {

    /**
     * The constant bit driving one input wire, or null if it is not (yet) known to be constant.
     *
     * Everything reads its inputs through one of these. In a module being simplified it means "the
     * source is a literal node"; while evaluating a callee it means "the environment computed a
     * value for that wire". Same folding code, two environments.
     */
    private fun interface Bits {
        fun of(wire: InputWire): Boolean?
    }

    /** Resolver for a module being simplified in place: a wire is constant if a literal drives it. */
    private val fromLiterals = Bits { wire ->
        val module = wire.parentWireVector.parentGroup.parentNode.parentModule
        val source = module.getConnectionForInputWire(wire).source
        val sourceNode = source.parentWireVector.parentGroup.parentNode
        if (sourceNode !is PredefinedFunctionNode) null
        else (sourceNode.predefinedFunction as? LiteralFunction)?.value?.testBit(source.index)
    }

    /** LSB-first bits driving [wires], or null if any of them is not constant. */
    private fun bitsOf(wires: List<InputWire>, bits: Bits): List<Boolean>? {
        val result = ArrayList<Boolean>(wires.size)
        wires.forEach { result.add(bits.of(it) ?: return null) }
        return result
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
    private fun fold(node: BodyNode, bits: Bits, depth: Int): List<Boolean>? {
        val width = node.outputWires().size

        if (node is PassThroughNode) return bitsOf(node.inputWires(), bits)
        if (node is ModuleInvocationNode) return foldInvocation(node, bits, depth)
        if (node !is PredefinedFunctionNode) return null

        return when (val function = node.predefinedFunction) {
            is LiteralFunction -> null
            is BinaryFunction -> {
                val lhs = valueOf(bitsOf(node.inputWireVectorGroups[0].wires(), bits) ?: return null)
                val rhs = valueOf(bitsOf(node.inputWireVectorGroups[1].wires(), bits) ?: return null)
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
                val input = bitsOf(node.inputWireVectorGroups[0].wires(), bits) ?: return null
                when (function) {
                    is BitwiseNotFunction -> input.map { !it }.take(width)
                    is LogicalNotFunction -> boolBits(input.none { it }, width)
                }
            }
            is RegisterFunction, is IntegerRegisterFunction ->
                bitsOf(node.inputWireVectorGroups[0].wires(), bits)
            is MuxFunction -> {
                val selector = valueOf(bitsOf(groupWires(node, "selector"), bits) ?: return null)
                val inputs = bitsOf(groupWires(node, "inputs"), bits) ?: return null
                // Out of range selects nothing: the emitted case statement has no default, so the
                // output latches its previous value rather than taking a constant.
                if (selector >= function.inputCount.toBigInteger()) return null
                val start = selector.toInt() * width
                if (start + width > inputs.size) return null
                inputs.subList(start, start + width)
            }
            is DemuxFunction -> {
                val selector = valueOf(bitsOf(groupWires(node, "selector"), bits) ?: return null)
                val input = bitsOf(groupWires(node, "input"), bits) ?: return null
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

                val conditions = bitsOf(conditionVectors.single().wires, bits) ?: return null
                val values = bitsOf(valueVectors.single().wires, bits) ?: return null
                if (values.size != width * function.conditionalCount) return null

                // First true condition wins, matching the emitted if / else-if chain.
                val taken = (0 until function.conditionalCount).firstOrNull { conditions.getOrElse(it) { false } }
                if (taken == null) bitsOf(groupWires(node, "default"), bits)
                else values.subList(taken * width, (taken + 1) * width)
            }
        }
    }

    /**
     * Folds a call to a user-defined function whose inputs are all constant, by evaluating the
     * callee rather than by inspecting it structurally.
     *
     * The invocation node's input and output groups are keyed by the callee's own port names - the
     * same correspondence `Flattener` uses when it inlines - so binding one to the other is a
     * lookup by identifier and a positional zip of the wires.
     */
    private fun foldInvocation(node: ModuleInvocationNode, bits: Bits, depth: Int): List<Boolean>? {
        if (depth >= MAX_EVALUATION_DEPTH) return null
        val callee = modulesByInvocation[node.invocation] ?: return null

        val arguments = node.inputWireVectorGroups.associate { group ->
            group.identifier to (bitsOf(group.wires(), bits) ?: return null)
        }

        val results = evaluate(callee, arguments, depth + 1) ?: return null

        // Concatenated in output-group order, which is the order of node.outputWires().
        return node.outputWireVectorGroups.flatMap { group ->
            val value = results[group.identifier] ?: return null
            if (value.size != group.wires().size) return null
            value
        }
    }

    /**
     * Runs [callee] on constant [arguments], returning a value per output port, or null if any of
     * them fails to resolve.
     *
     * A plain dataflow fixpoint: seed the input ports and every literal, then repeatedly fold any
     * node all of whose inputs are known. It terminates because the environment only grows, and a
     * node that never resolves - a register in a feedback loop, whose input depends on its own
     * output - simply leaves the fixpoint early with null.
     */
    private fun evaluate(
        callee: Module,
        arguments: Map<String, List<Boolean>>,
        depth: Int,
    ): Map<String, List<Boolean>>? {
        val values = IdentityHashMap<OutputWire, Boolean>()

        callee.getInputNodes().forEach { port ->
            val value = arguments[port.name()] ?: return null
            val wires = port.outputWires()
            if (value.size != wires.size) return null
            wires.forEachIndexed { index, wire -> values[wire] = value[index] }
        }

        callee.getBodyNodes().forEach { node ->
            val literal = (node as? PredefinedFunctionNode)?.predefinedFunction as? LiteralFunction
                ?: return@forEach
            node.outputWires().forEach { values[it] = literal.value.testBit(it.index) }
        }

        val environment = Bits { wire -> values[callee.getConnectionForInputWire(wire).source] }

        val pending = callee.getBodyNodes()
            .filter { (it as? PredefinedFunctionNode)?.predefinedFunction !is LiteralFunction }
            .toMutableList()

        while (pending.isNotEmpty()) {
            val resolved = pending.filter { node ->
                val folded = fold(node, environment, depth) ?: return@filter false
                val wires = node.outputWires()
                if (folded.size != wires.size) return@filter false
                wires.forEachIndexed { index, wire -> values[wire] = folded[index] }
                true
            }
            if (resolved.isEmpty()) return null
            pending.removeAll(resolved)
        }

        return callee.getOutputNodes().associate { port ->
            port.name() to (bitsOf(port.inputWires(), environment) ?: return null)
        }
    }

    private var counter = 0
    private var modulesByInvocation: Map<Module.Invocation, Module> = emptyMap()

    private const val MAX_EVALUATION_DEPTH = 32

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
        var calls = 0

        while (true) {
            val batch = module.getBodyNodes()
                .filter { it !is PredefinedFunctionNode || it.predefinedFunction !is LiteralFunction }
                .mapNotNull { node -> fold(node, fromLiterals, 0)?.let { node to it } }
            if (batch.isEmpty()) break

            batch.forEach { (node, bits) ->
                if (node is ModuleInvocationNode) calls++
                swapIn(module, node, literalNodeFor(module, bits))
                node.inputWires().forEach { module.disconnect(it) }
                module.removeNode(node)
            }
            folded += batch.size
        }

        if (folded > 0) {
            Logger.debug {
                "Constant-folded $folded node(s), $calls of them calls, " +
                    "in ${original.invocation.gaplFunctionName}"
            }
        }
        return module
    }

    override fun transform(original: List<Module>): List<Module> {
        // Callees are evaluated from the untransformed list: folding is semantic, so simplifying a
        // module does not change what a call to it returns.
        modulesByInvocation = original.associateBy { it.invocation }
        return original.map { simplify(it) }
    }
}
