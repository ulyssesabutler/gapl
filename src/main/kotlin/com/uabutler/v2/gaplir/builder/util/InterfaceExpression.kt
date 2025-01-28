package com.uabutler.v2.gaplir.builder.util

import com.uabutler.v2.ast.node.interfaces.InterfaceExpressionNode

// TODO
sealed interface InterfaceExpression {
    companion object {
        fun fromNode(node: InterfaceExpressionNode): InterfaceExpression = TODO()
    }
}