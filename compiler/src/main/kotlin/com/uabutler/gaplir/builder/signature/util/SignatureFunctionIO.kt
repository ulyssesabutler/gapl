package com.uabutler.gaplir.builder.signature.util

import com.uabutler.gaplir.builder.util.InterfaceExpression

data class SignatureFunctionIO(
    val identifier: String,
    val dataType: InterfaceExpression,
    val ioType: SignatureFunctionIOType,
)

sealed interface SignatureFunctionIOType

data object SignatureFunctionIOTypeDefault: SignatureFunctionIOType
data object SignatureFunctionIOTypeSequential: SignatureFunctionIOType
data object SignatureFunctionIOTypeCombinational: SignatureFunctionIOType
