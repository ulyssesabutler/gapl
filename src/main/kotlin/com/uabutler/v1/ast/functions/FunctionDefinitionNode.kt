package com.uabutler.v1.ast.functions

import com.uabutler.v1.ast.*
import com.uabutler.v1.ast.functions.circuits.CircuitStatementNode
import com.uabutler.v1.references.FunctionScope
import com.uabutler.v1.references.Scope

data class FunctionDefinitionNode(
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
    val inputFunctionIO: List<FunctionIONode>,
    val outputFunctionIO: List<FunctionIONode>,
    val statements: List<CircuitStatementNode>,
): PersistentNode, ScopeNode {
    override var parent: PersistentNode? = null
    override var associatedScope: Scope? = null
    fun functionScope() = associatedScope?.let { if (it is FunctionScope) it else null }
}