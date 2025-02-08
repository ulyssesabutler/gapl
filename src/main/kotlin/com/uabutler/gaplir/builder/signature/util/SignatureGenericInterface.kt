package com.uabutler.gaplir.builder.signature.util

import com.uabutler.ast.node.GenericInterfaceDefinitionNode

data class SignatureGenericInterface(
    val identifier: String
) {
    companion object {
        fun fromNode(node: GenericInterfaceDefinitionNode): SignatureGenericInterface {
            return SignatureGenericInterface(identifier = node.identifier.value)
        }
    }
}