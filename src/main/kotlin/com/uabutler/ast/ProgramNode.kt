package com.uabutler.ast

import com.uabutler.Parser
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.interfaces.InterfaceDefinitionNode
import com.uabutler.references.ProgramScope
import com.uabutler.references.Scope
import com.uabutler.visitor.ProgramVisitor

/**
 * The root node for our parsed programs
 */
data class ProgramNode(
    val interfaces: List<InterfaceDefinitionNode>,
    val functions: List<FunctionDefinitionNode>,
): PersistentNode, ScopeNode {
    override var scope: Scope? = null

    companion object {
        fun fromParser(parser: Parser): ProgramNode {
            return ProgramVisitor.visitProgram(parser.program())
        }
    }
}
