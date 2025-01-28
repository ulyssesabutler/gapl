package com.uabutler.v2.gaplir.builder.definition

import com.uabutler.v2.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.v2.gaplir.builder.signature.InterfaceSignature

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