package com.uabutler.ast

import com.uabutler.Parser
import com.uabutler.ast.interfaces.InterfaceDefinitionNode
import com.uabutler.visitor.ASTVisitor

/**
 * The root node for our parsed programs
 */
data class ProgramNode(val interfaces: List<InterfaceDefinitionNode>): PersistentNode {
    companion object {
        fun fromParser(parser: Parser): ProgramNode {
            return ASTVisitor().visitProgram(parser.program())
        }
    }
}
