package com.uabutler.ast.node

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode

/**
 * The root node for our parsed programs
 */
data class ProgramNode(
    val interfaces: List<InterfaceDefinitionNode>,
    val functions: List<FunctionDefinitionNode>,
): PersistentNode
