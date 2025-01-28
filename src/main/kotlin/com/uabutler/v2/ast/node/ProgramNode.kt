package com.uabutler.v2.ast.node

import com.uabutler.v2.ast.node.functions.FunctionDefinitionNode
import com.uabutler.v2.ast.node.interfaces.InterfaceDefinitionNode

/**
 * The root node for our parsed programs
 */
data class ProgramNode(
    val interfaces: List<InterfaceDefinitionNode>,
    val functions: List<FunctionDefinitionNode>,
): PersistentNode
