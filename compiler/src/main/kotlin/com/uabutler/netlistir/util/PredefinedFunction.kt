package com.uabutler.netlistir.util

import com.uabutler.gaplir.builder.util.AdditionFunction
import com.uabutler.gaplir.builder.util.AndFunction
import com.uabutler.gaplir.builder.util.BitwiseAndFunction
import com.uabutler.gaplir.builder.util.BitwiseOrFunction
import com.uabutler.gaplir.builder.util.BitwiseXorFunction
import com.uabutler.gaplir.builder.util.EqualsFunction
import com.uabutler.gaplir.builder.util.GreaterThanEqualsFunction
import com.uabutler.gaplir.builder.util.LeftShiftFunction
import com.uabutler.gaplir.builder.util.LessThanEqualsFunction
import com.uabutler.gaplir.builder.util.LiteralFunction
import com.uabutler.gaplir.builder.util.MultiplicationFunction
import com.uabutler.gaplir.builder.util.NotEqualsFunction
import com.uabutler.gaplir.builder.util.OrFunction
import com.uabutler.gaplir.builder.util.RegisterFunction
import com.uabutler.gaplir.builder.util.RightShiftFunction
import com.uabutler.gaplir.builder.util.SubtractionFunction
import com.uabutler.gaplir.builder.util.PredefinedFunction as NetlistPredefinedFunction

sealed class PredefinedFunction {
    companion object {
        fun fromGapl(gaplPredefinedFunction: NetlistPredefinedFunction): PredefinedFunction {
            return when (gaplPredefinedFunction) {
                is AdditionFunction -> AdditionFunction
                is BitwiseAndFunction -> BitwiseAndFunction
                is BitwiseOrFunction -> BitwiseOrFunction
                is BitwiseXorFunction -> BitwiseXorFunction
                is LeftShiftFunction -> LeftShiftFunction
                is MultiplicationFunction -> MultiplicationFunction
                is RightShiftFunction -> RightShiftFunction
                is SubtractionFunction -> SubtractionFunction
                is GreaterThanEqualsFunction -> GreaterThanEqualsFunction
                is LessThanEqualsFunction -> LessThanEqualsFunction
                is EqualsFunction -> EqualsFunction
                is NotEqualsFunction -> NotEqualsFunction
                is AndFunction -> LogicalAndFunction
                is OrFunction -> LogicalOrFunction
                is LiteralFunction -> LiteralFunction(gaplPredefinedFunction.value)
                is RegisterFunction -> RegisterFunction
            }
        }
    }
}

data object EqualsFunction: PredefinedFunction()
data object NotEqualsFunction: PredefinedFunction()

data object LessThanEqualsFunction: PredefinedFunction()
data object GreaterThanEqualsFunction: PredefinedFunction()

data object LogicalAndFunction: PredefinedFunction()
data object LogicalOrFunction: PredefinedFunction()

data object BitwiseAndFunction: PredefinedFunction()
data object BitwiseOrFunction: PredefinedFunction()
data object BitwiseXorFunction: PredefinedFunction()

data object AdditionFunction: PredefinedFunction()
data object SubtractionFunction: PredefinedFunction()
data object MultiplicationFunction: PredefinedFunction()

data object RightShiftFunction: PredefinedFunction()
data object LeftShiftFunction: PredefinedFunction()

data object RegisterFunction: PredefinedFunction()

data class LiteralFunction(val value: Int): PredefinedFunction()
