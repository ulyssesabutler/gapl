package com.uabutler.ast.node.interfaces

import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode

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
): InterfaceDefinitionNode

data class RecordInterfaceDefinitionNode(
    override val identifier: IdentifierNode,
    override val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    override val genericParameters: List<GenericParameterDefinitionNode>,
    val inherits: List<DefinedInterfaceExpressionNode>, // TODO: We should also support identifier expressions
    val ports: List<RecordInterfacePortNode>,
): InterfaceDefinitionNode
