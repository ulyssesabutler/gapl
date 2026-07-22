package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode

data class InterfaceSignature(
    val identifier: String,
    val genericInterfaces: List<SignatureGenericInterface>,
    val genericParameters: List<SignatureGenericParameter>
) {
    companion object {
        fun fromNode(node: InterfaceDefinitionNode): InterfaceSignature {
            return InterfaceSignature(
                identifier = node.identifier.value,
                genericInterfaces = node.genericInterfaces.map { SignatureGenericInterface(it.identifier.value) },
                genericParameters = node.genericParameters.map {
                    SignatureGenericParameter(
                        identifier = it.identifier.value,
                        type = Integer // TODO:
                    )
                }
            )
        }
    }
}