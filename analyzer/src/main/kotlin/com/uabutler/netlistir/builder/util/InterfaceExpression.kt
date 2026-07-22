package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

sealed interface InterfaceExpression {
    companion object {
        fun fromNode(node: InterfaceExpressionNode): InterfaceExpression = TODO()
    }
}