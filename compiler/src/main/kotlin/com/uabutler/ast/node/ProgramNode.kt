package com.uabutler.ast.node

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.diagnostics.SourceSpan

/**
 * The root node for our parsed programs
 */
data class ProgramNode(
    override val span: SourceSpan,
    val interfaces: List<InterfaceDefinitionNode>,
    val functions: List<FunctionDefinitionNode>,
): GAPLNode
