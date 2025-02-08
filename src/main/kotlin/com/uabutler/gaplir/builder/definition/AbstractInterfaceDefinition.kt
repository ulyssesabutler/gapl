package com.uabutler.gaplir.builder.definition

import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.gaplir.builder.signature.InterfaceSignature

data class AbstractInterfaceDefinition(
    val signature: InterfaceSignature,
    // TODO: Tmp way to add structure
    val node: InterfaceDefinitionNode,
) {
    companion object {
        fun fromNode(node: InterfaceDefinitionNode): AbstractInterfaceDefinition {
            return AbstractInterfaceDefinition(
                signature = InterfaceSignature.fromNode(node),
                // TODO: TMP
                node = node,
            )
        }
    }
}