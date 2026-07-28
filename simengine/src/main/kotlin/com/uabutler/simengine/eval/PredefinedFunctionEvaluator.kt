package com.uabutler.simengine.eval

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.AdditionFunction
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

/**
 * Pure per-op bit computation: given a [PredefinedFunctionNode] and a way to read its input bits,
 * compute its output bits. Needs no [com.uabutler.netlistir.netlist.Module] and holds no state of
 * its own — register nodes are handled exclusively by `instance.ModuleInstance.latchRegisters()`,
 * never dispatched here.
 */
object PredefinedFunctionEvaluator {

    fun evaluate(node: PredefinedFunctionNode, read: (InputWire) -> Boolean): List<Boolean> {
        fun group(name: String): List<Boolean> =
            node.inputWireVectorGroups.first { it.identifier == name }.wires().map(read)

        fun groupBig(name: String) = bitsToUnsignedBigInteger(group(name))

        return when (val fn = node.predefinedFunction) {
            is EqualsFunction -> listOf(groupBig("lhs") == groupBig("rhs"))
            is NotEqualsFunction -> listOf(groupBig("lhs") != groupBig("rhs"))
            is LessThanEqualsFunction -> listOf(groupBig("lhs") <= groupBig("rhs"))
            is GreaterThanEqualsFunction -> listOf(groupBig("lhs") >= groupBig("rhs"))
            is LessThanFunction -> listOf(groupBig("lhs") < groupBig("rhs"))
            is GreaterThanFunction -> listOf(groupBig("lhs") > groupBig("rhs"))

            LogicalAndFunction -> listOf(group("lhs")[0] && group("rhs")[0])
            LogicalOrFunction -> listOf(group("lhs")[0] || group("rhs")[0])
            LogicalNotFunction -> listOf(!group("input")[0])

            is BitwiseAndFunction -> group("lhs").zip(group("rhs")) { a, b -> a && b }
            is BitwiseOrFunction -> group("lhs").zip(group("rhs")) { a, b -> a || b }
            is BitwiseXorFunction -> group("lhs").zip(group("rhs")) { a, b -> a xor b }
            is BitwiseNotFunction -> group("input").map { !it }

            is AdditionFunction -> unsignedBigIntegerToBits(groupBig("lhs") + groupBig("rhs"), fn.size)
            is SubtractionFunction -> unsignedBigIntegerToBits(groupBig("lhs") - groupBig("rhs"), fn.size)
            is MultiplicationFunction -> unsignedBigIntegerToBits(groupBig("lhs") * groupBig("rhs"), fn.size)
            is LeftShiftFunction -> unsignedBigIntegerToBits(groupBig("lhs").shiftLeft(groupBig("rhs").toInt()), fn.size)
            is RightShiftFunction -> unsignedBigIntegerToBits(groupBig("lhs").shiftRight(groupBig("rhs").toInt()), fn.size)

            is LiteralFunction -> unsignedBigIntegerToBits(fn.value, fn.size)

            is MuxFunction -> {
                val selected = groupBig("selector").toInt()
                if (selected >= fn.inputCount) {
                    error("Mux selector $selected out of range [0, ${fn.inputCount}) in ${node.name()}")
                }
                val inputsGroup = node.inputWireVectorGroups.first { it.identifier == "inputs" }
                val outputGroup = node.outputWireVectorGroups.first { it.identifier == "output" }
                outputGroup.wireVectors.zip(inputsGroup.wireVectors) { outVec, inVec ->
                    val block = outVec.wires.size
                    inVec.wires.subList(selected * block, selected * block + block).map(read)
                }.flatten()
            }

            is DemuxFunction -> {
                val selected = groupBig("selector").toInt()
                val inputGroup = node.inputWireVectorGroups.first { it.identifier == "input" }
                val outputsGroup = node.outputWireVectorGroups.first { it.identifier == "outputs" }
                outputsGroup.wireVectors.zip(inputGroup.wireVectors) { outVec, inVec ->
                    val block = inVec.wires.size
                    val result = MutableList(outVec.wires.size) { false }
                    if (selected < fn.outputCount) {
                        inVec.wires.map(read).forEachIndexed { i, v -> result[selected * block + i] = v }
                    }
                    result
                }.flatten()
            }

            is PriorityFunction -> {
                val conditionalsGroup = node.inputWireVectorGroups.first { it.identifier == "conditionals" }
                val conditionVec = conditionalsGroup.wireVectors.first { it.identifier.firstOrNull() == "condition" }
                val valueVecs = conditionalsGroup.wireVectors.filter { it.identifier.firstOrNull() == "value" }
                val winner = (0 until fn.conditionalCount).firstOrNull { read(conditionVec.wires[it]) }
                val defaultGroup = node.inputWireVectorGroups.first { it.identifier == "default" }
                if (winner == null) {
                    defaultGroup.wires().map(read)
                } else {
                    valueVecs.map { vec ->
                        val block = vec.wires.size / fn.conditionalCount
                        vec.wires.subList(winner * block, winner * block + block).map(read)
                    }.flatten()
                }
            }

            is RegisterFunction, is IntegerRegisterFunction ->
                error("register nodes are handled by ModuleInstance.latchRegisters(), never dispatched to eval")
        }
    }
}
