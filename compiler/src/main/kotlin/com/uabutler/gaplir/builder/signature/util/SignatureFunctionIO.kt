package com.uabutler.gaplir.builder.signature.util

import com.uabutler.ast.node.functions.*
import com.uabutler.gaplir.builder.util.InterfaceExpression

data class SignatureFunctionIO(
    val identifier: String,
    val dataType: InterfaceExpression,
    val ioType: SignatureFunctionIOType,
) {
    companion object {
        fun fromNode(node: FunctionIONode): SignatureFunctionIO {
            return SignatureFunctionIO(
                identifier = node.identifier.value,
                dataType = InterfaceExpression.fromNode(node.interfaceType),
                ioType = SignatureFunctionIOType.fromNode(node.ioType)
            )
        }
    }
}

sealed interface SignatureFunctionIOType {
    companion object {
        fun fromNode(node: FunctionIOTypeNode): SignatureFunctionIOType {
            return when (node) {
                is DefaultFunctionIOTypeNode -> SignatureFunctionIOTypeDefault
                is SequentialFunctionIOTypeNode -> SignatureFunctionIOTypeSequential
                is CombinationalFunctionIOTypeNode -> SignatureFunctionIOTypeCombinational
            }
        }
    }
}

data object SignatureFunctionIOTypeDefault: SignatureFunctionIOType
data object SignatureFunctionIOTypeSequential: SignatureFunctionIOType
data object SignatureFunctionIOTypeCombinational: SignatureFunctionIOType
