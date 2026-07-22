package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode

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