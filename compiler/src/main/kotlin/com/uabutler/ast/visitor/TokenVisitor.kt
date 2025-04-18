package com.uabutler.ast.visitor

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.IntegerLiteralNode
import org.antlr.v4.kotlinruntime.Token
import org.antlr.v4.kotlinruntime.tree.TerminalNode

object TokenVisitor {
    fun visitId(node: TerminalNode): IdentifierNode {
        return IdentifierNode(node.text)
    }

    fun visitId(token: Token?): IdentifierNode {
        return IdentifierNode(token!!.text!!)
    }

    fun visitIntegerLiteral(token: TerminalNode): IntegerLiteralNode {
        return IntegerLiteralNode(token.text.toInt())
    }
}
