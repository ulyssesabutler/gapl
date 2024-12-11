package com.uabutler.ast.interfaces

import com.uabutler.ast.*
import com.uabutler.references.InterfaceScope
import com.uabutler.references.Scope

sealed interface InterfaceDefinitionNode: PersistentNode {
    val identifier: IdentifierNode
    val genericInterfaces: List<GenericInterfaceDefinitionNode>
    val genericParameters: List<GenericParameterDefinitionNode>
}

data class AliasInterfaceDefinitionNode(
    override val identifier: IdentifierNode,
    override val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    override val genericParameters: List<GenericParameterDefinitionNode>,
    val aliasedInterface: InterfaceExpressionNode,
): InterfaceDefinitionNode {
    override var parent: PersistentNode? = null
}

data class RecordInterfaceDefinitionNode(
    override val identifier: IdentifierNode,
    override val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    override val genericParameters: List<GenericParameterDefinitionNode>,
    val inherits: List<DefinedInterfaceExpressionNode>, // TODO: We should also support identifier expressions
    val ports: List<RecordInterfacePortNode>,
): InterfaceDefinitionNode, ScopeNode {
    override var parent: PersistentNode? = null
    override var associatedScope: Scope? = null
}
