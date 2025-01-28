package com.uabutler.v2.gaplir

import com.uabutler.v2.ast.node.interfaces.InterfaceExpressionNode

sealed interface InterfaceStructure {
    companion object {
        fun fromNode(node: InterfaceExpressionNode): InterfaceStructure = TODO()
    }
}

data object WireInterfaceStructure: InterfaceStructure

data class RecordInterfaceStructure(
    val ports: Map<String, InterfaceStructure>
): InterfaceStructure

data class VectorInterfaceStructure(
    val vectoredInterface: InterfaceStructure,
    val size: Int
): InterfaceStructure