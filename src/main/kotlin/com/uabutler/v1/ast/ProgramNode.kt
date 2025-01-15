package com.uabutler.v1.ast

import com.uabutler.v1.Parser
import com.uabutler.v1.ast.functions.FunctionDefinitionNode
import com.uabutler.v1.ast.interfaces.InterfaceDefinitionNode
import com.uabutler.v1.references.ProgramScope
import com.uabutler.v1.references.Scope
import com.uabutler.v1.visitor.ProgramVisitor

/**
 * The root node for our parsed programs
 */
data class ProgramNode(
    val interfaces: List<InterfaceDefinitionNode>,
    val functions: List<FunctionDefinitionNode>,
): PersistentNode, ScopeNode {
    override var parent: PersistentNode? = null
    override var associatedScope: Scope? = null
    fun programScope() = associatedScope?.let { if (it is ProgramScope) it else null }

    companion object {
        fun fromParser(parser: Parser): ProgramNode {
            return ProgramVisitor.visitProgram(parser.program())
        }
    }
}
