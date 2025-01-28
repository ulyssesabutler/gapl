package com.uabutler.v2.gaplir.builder.signature

import com.uabutler.v2.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.v2.gaplir.builder.signature.util.Integer
import com.uabutler.v2.gaplir.builder.signature.util.SignatureGenericInterface
import com.uabutler.v2.gaplir.builder.signature.util.SignatureGenericParameter

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