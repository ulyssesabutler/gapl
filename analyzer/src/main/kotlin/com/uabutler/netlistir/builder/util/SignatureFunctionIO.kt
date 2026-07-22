package com.uabutler.netlistir.builder.util

data class SignatureFunctionIO(
    val identifier: String,
    val dataType: InterfaceExpression,
    val ioType: SignatureFunctionIOType,
)

sealed interface SignatureFunctionIOType

data object SignatureFunctionIOTypeDefault: SignatureFunctionIOType
data object SignatureFunctionIOTypeSequential: SignatureFunctionIOType
data object SignatureFunctionIOTypeCombinational: SignatureFunctionIOType
