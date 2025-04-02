package com.uabutler.gaplir.builder.util

import com.uabutler.ast.node.interfaces.InterfaceExpressionNode

// TODO
sealed interface InterfaceExpression {
    companion object {
        fun fromNode(node: InterfaceExpressionNode): InterfaceExpression = TODO()
    }
}