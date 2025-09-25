package com.uabutler.util

import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.BitwiseAndFunction
import com.uabutler.netlistir.util.BitwiseNotFunction
import com.uabutler.netlistir.util.BitwiseOrFunction
import com.uabutler.netlistir.util.BitwiseXorFunction
import com.uabutler.netlistir.util.EqualsFunction
import com.uabutler.netlistir.util.GreaterThanEqualsFunction
import com.uabutler.netlistir.util.GreaterThanFunction
import com.uabutler.netlistir.util.LeftShiftFunction
import com.uabutler.netlistir.util.LessThanEqualsFunction
import com.uabutler.netlistir.util.LessThanFunction
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.LogicalAndFunction
import com.uabutler.netlistir.util.LogicalNotFunction
import com.uabutler.netlistir.util.LogicalOrFunction
import com.uabutler.netlistir.util.MultiplicationFunction
import com.uabutler.netlistir.util.NotEqualsFunction
import com.uabutler.netlistir.util.PredefinedFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.netlistir.util.RightShiftFunction
import com.uabutler.netlistir.util.SubtractionFunction
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.reflect.KClass

class YamlDelayModel(file: File): PropagationDelay {

    private data class Slice(
        val bitWidthRange: IntRange,
        val delay: Int,
    )

    private class OperatorDelayModel(
        slices: List<Slice>,
        private val default: Int,
    ) {
        val sortedSlices = slices.sortedBy { it.bitWidthRange.first }

        init {
            sortedSlices.zipWithNext().forEach { (a, b) ->
                if (a.bitWidthRange.last >= b.bitWidthRange.first) {
                    throw Exception("Overlapping ranges in delay model")
                }
            }
        }

        fun getDelay(bitWidth: Int): Int {
            val index = sortedSlices.binarySearch {
                when {
                    it.bitWidthRange.contains(bitWidth) -> 0
                    it.bitWidthRange.last < bitWidth -> 1
                    else -> -1
                }
            }

            if (index < 0 || !sortedSlices[index].bitWidthRange.contains(bitWidth)) {
                return default
            }

            return sortedSlices[index].delay
        }
    }

    private val delayModel: Map<KClass<out PredefinedFunction>, OperatorDelayModel> = mutableMapOf()
    private val default: Int

    init {
        val yaml = Yaml()
        val content = yaml.load<Map<String, Any>>(file.inputStream())
        
        // Parse the default value for the entire model
        default = content["default"] as? Int ?: 0
        
        // Parse each function's delay model
        content.forEach { (key, value) ->
            if (key != "default") {
                try {
                    val functionClass = stringToPredefinedFunction(key)
                    @Suppress("UNCHECKED_CAST")
                    val operatorModel = parseOperatorModel(value as Map<String, Any>)
                    (delayModel as MutableMap)[functionClass] = operatorModel
                } catch (e: Exception) {
                    throw Exception("Error parsing delay model for function '$key': ${e.message}")
                }
            }
        }
    }
    
    private fun parseOperatorModel(modelData: Map<String, Any>): OperatorDelayModel {
        val defaultDelay = modelData["default"] as? Int ?: 0
        val slices = mutableListOf<Slice>()

        @Suppress("UNCHECKED_CAST")
        val widthsData = modelData["widths"] as? List<Map<String, Any>> ?: emptyList()
        
        for (sliceData in widthsData) {
            val index = sliceData["index"] as Int
            val until = sliceData["until"] as? Int
            val delay = sliceData["delay"] as Int
            
            val range = if (until != null) {
                IntRange(index, until - 1) // until is exclusive in the range
            } else {
                IntRange(index, Int.MAX_VALUE)
            }
            
            slices.add(Slice(range, delay))
        }
        
        return OperatorDelayModel(slices, defaultDelay)
    }

    private fun stringToPredefinedFunction(value: String): KClass<out PredefinedFunction> {
        return when (value) {
            "lessThan" -> LessThanFunction::class
            "greaterThan" -> GreaterThanFunction::class
            "lessThanEquals" -> LessThanEqualsFunction::class
            "greaterThanEquals" -> GreaterThanEqualsFunction::class
            "equals" -> EqualsFunction::class
            "notEquals" -> NotEqualsFunction::class
            "logicalAnd" -> LogicalAndFunction::class
            "logicalOr" -> LogicalOrFunction::class
            "logicalNot" -> LogicalNotFunction::class
            "bitwiseAnd" -> BitwiseAndFunction::class
            "bitwiseOr" -> BitwiseOrFunction::class
            "bitwiseXor" -> BitwiseXorFunction::class
            "bitwiseNot" -> BitwiseNotFunction::class
            "addition" -> AdditionFunction::class
            "subtraction" -> SubtractionFunction::class
            "multiplication" -> MultiplicationFunction::class
            "leftShift" -> LeftShiftFunction::class
            "rightShift" -> RightShiftFunction::class
            else -> throw Exception("Unknown function type: $value")
        }
    }

    override fun forNode(node: Node): Int {
        if (node !is PredefinedFunctionNode) return default

        val operatorDelayModel = delayModel[node.predefinedFunction::class]

        return operatorDelayModel?.getDelay(node.outputWires().size) ?: default
    }
}