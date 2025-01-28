package com.uabutler.v2.gaplir.builder.signature.util

import com.uabutler.v2.ast.node.GenericInterfaceDefinitionNode

data class SignatureGenericInterface(
    val identifier: String
) {
    companion object {
        fun fromNode(node: GenericInterfaceDefinitionNode): SignatureGenericInterface {
            return SignatureGenericInterface(identifier = node.identifier.value)
        }
    }
}