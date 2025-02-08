package com.uabutler.gaplir.builder.signature.util

import com.uabutler.ast.node.GenericParameterDefinitionNode

data class SignatureGenericParameter(
    val identifier: String,
    val type: SignatureGenericParameterType,
) {
    companion object {
        fun fromNode(node: GenericParameterDefinitionNode): SignatureGenericParameter {
            return SignatureGenericParameter(
                identifier = node.identifier.value,
                type = when (node.typeIdentifier.value) {
                    "integer" -> Integer
                    else -> throw Exception("Unknown type: ${node.typeIdentifier.value}")
                }
            )
        }
    }
}