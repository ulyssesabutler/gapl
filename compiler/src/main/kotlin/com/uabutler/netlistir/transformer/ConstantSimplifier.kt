package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputWire
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
import java.math.BigInteger

object ConstantSimplifier: Transformer {

    private fun isWireSourceLiteral(wire: InputWire): Boolean {
        val module = wire.parentWireVector.parentGroup.parentNode.parentModule

        val sourceNode = module.getConnectionForInputWire(wire).source.parentWireVector.parentGroup.parentNode

        if (sourceNode !is PredefinedFunctionNode) return false

        if (sourceNode.predefinedFunction !is LiteralFunction) return false

        return true
    }

    private fun isNodeSourceLiteral(node: BodyNode): Boolean {
        if (node.inputWires().isEmpty()) return false
        return node.inputWires().all { isWireSourceLiteral(it) }
    }

    private fun isCollapsibleNode(node: BodyNode): Boolean {
        if (node is PassThroughNode || node is PredefinedFunctionNode) {
            return isNodeSourceLiteral(node)
        }

        return false
    }

    private fun createBitArray(literal: BigInteger, size: Int): List<Boolean> {
        return literal.toString(2).padStart(size, '0').reversed().map { it == '1' }
    }

    private fun createLiteral(bitArray: List<Boolean>): BigInteger {
        return BigInteger(bitArray.joinToString(separator = "") { if (it) "1" else "0" }, 2)
    }

    private fun inputWiresToBitArray(inputWires: List<InputWire>): List<Boolean> {
        val module = inputWires.first().parentWireVector.parentGroup.parentNode.parentModule
        return inputWires.map { inputWire ->
            val connection = module.getConnectionForInputWire(inputWire)
            val outputWire = connection.source
            val index = outputWire.index
            val sourceNode = outputWire.parentWireVector.parentGroup.parentNode

            if (sourceNode !is PredefinedFunctionNode) throw IllegalStateException("Expected source node to be a PredefinedFunctionNode")

            val literalFunction = sourceNode.predefinedFunction
            if (literalFunction !is LiteralFunction) throw IllegalStateException("Expected source node to be a literal function")

            literalFunction.value.testBit(index)
        }
    }

    private fun createBitArrayFromNode(node: BodyNode): List<Boolean> {
        if (node is PassThroughNode) {
            return inputWiresToBitArray(node.inputWires())
        }

        if (node is PredefinedFunctionNode) {
            when (val function = node.predefinedFunction) {
                is LiteralFunction -> throw IllegalStateException("Literal function should not be a PredefinedFunctionNode")
                is BinaryFunction -> {
                    val lhs = createLiteral(inputWiresToBitArray(node.inputWireVectorGroups[0].wires()))
                    val rhs = createLiteral(inputWiresToBitArray(node.inputWireVectorGroups[1].wires()))

                    val size = node.outputWires().size

                    return when (function) {
                        is AdditionFunction -> createBitArray(lhs + rhs, size)
                        is BitwiseAndFunction -> createBitArray(lhs and rhs, size)
                        is BitwiseOrFunction -> createBitArray(lhs or rhs, size)
                        is BitwiseXorFunction -> createBitArray(lhs xor rhs, size)
                        is LeftShiftFunction -> createBitArray(lhs shl rhs.toInt(),  size)
                        is MultiplicationFunction -> createBitArray(lhs * rhs, size)
                        is RightShiftFunction -> createBitArray(lhs shr rhs.toInt(), size)
                        is SubtractionFunction -> createBitArray(lhs - rhs, size)
                        LogicalAndFunction -> createBitArray(lhs and rhs, size)
                        LogicalOrFunction -> createBitArray(lhs or rhs, size)
                        is EqualsFunction -> createBitArray(if (lhs == rhs) BigInteger.ONE else BigInteger.ZERO, size)
                        is GreaterThanEqualsFunction -> createBitArray(if (lhs >= rhs) BigInteger.ONE else BigInteger.ZERO, size)
                        is GreaterThanFunction -> createBitArray(if (lhs > rhs) BigInteger.ONE else BigInteger.ZERO, size)
                        is LessThanEqualsFunction -> createBitArray(if (lhs <= rhs) BigInteger.ONE else BigInteger.ZERO, size)
                        is LessThanFunction -> createBitArray(if (lhs < rhs) BigInteger.ONE else BigInteger.ZERO, size)
                        is NotEqualsFunction -> createBitArray(if (lhs != rhs) BigInteger.ONE else BigInteger.ZERO, size)
                    }
                }
                is UnaryFunction -> {
                    val input = createLiteral(inputWiresToBitArray(node.inputWireVectorGroups[0].wires()))

                    val size = node.outputWires().size

                    return when (function) {
                        is BitwiseNotFunction -> createBitArray(input.not(), size)
                        is LogicalNotFunction -> createBitArray(if (input == BigInteger.ZERO) BigInteger.ONE else BigInteger.ZERO, size)
                    }
                }
                is RegisterFunction -> return inputWiresToBitArray(node.inputWireVectorGroups[0].wires())
                is IntegerRegisterFunction -> return inputWiresToBitArray(node.inputWireVectorGroups[0].wires())
                is MuxFunction -> TODO("dont wanna")
                is DemuxFunction -> TODO("dont wanna")
                is PriorityFunction -> TODO("dont wanna")
            }
        }

        throw IllegalStateException("Unexpected node type")
    }

    private fun createCollapsedLiteralNode(node: BodyNode): PredefinedFunctionNode {
        val module = node.parentModule

        val bitArray = createBitArrayFromNode(node)

        val literalFunction = LiteralFunction(bitArray.size, createLiteral(bitArray))

        return PredefinedFunctionNode(
            identifier = "COLLAPSED$${bitArray.size}$${literalFunction.value}",
            parentModule = module,
            inputWireVectorGroupsBuilder = { node ->
                literalFunction.inputs.map { it.toInputWireVectorGroup(node) }
            },
            outputWireVectorGroupsBuilder = { node ->
                literalFunction.outputs.map { it.toOutputWireVectorGroup(node) }
            },
            predefinedFunction = literalFunction,
        ).also { module.addBodyNode(it) }
    }

    private fun swapInLiteralNode(originalNode: BodyNode, newNode: PredefinedFunctionNode) {
        val module = originalNode.parentModule
        val originalNodeToNewNodeOutputWire = originalNode.outputWires().zip(newNode.outputWires()).toMap()

        originalNode.outputWires().forEach {
            module.getConnectionsForOutputWire(it).forEach { connection ->
                module.disconnect(connection.sink)
                module.connect(connection.sink, originalNodeToNewNodeOutputWire[connection.source]!!)
            }
        }
    }

    private fun simplify(module: MutableModule): MutableModule {
        do {
            val collapsibleNodes = module.getBodyNodes().filter { isCollapsibleNode(it) }
            val collapsedNodes = collapsibleNodes.associateWith { createCollapsedLiteralNode(it) }
            collapsedNodes.forEach { (originalNode, newNode) -> swapInLiteralNode(originalNode, newNode) }
            collapsibleNodes.forEach {
                it.inputWires().forEach { module.disconnect(it)}
                module.removeNode(it)
            }
        } while (collapsibleNodes.isNotEmpty())

        return module
    }

    override fun transform(original: List<MutableModule>): List<MutableModule> {
        return original.map { simplify(it) }
    }
}