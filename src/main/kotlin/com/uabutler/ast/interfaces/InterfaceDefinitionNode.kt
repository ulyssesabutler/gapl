package com.uabutler.ast.interfaces

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode

sealed class InterfaceDefinitionNode(
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
): PersistentNode()

class AliasInterfaceDefinitionNode(
    identifier: IdentifierNode,
    genericInterfaces: List<GenericInterfaceDefinitionNode>,
    genericParameters: List<GenericParameterDefinitionNode>,
    val aliasedInterface: InterfaceExpressionNode,
): InterfaceDefinitionNode(identifier, genericInterfaces, genericParameters)

class RecordInterfaceDefinitionNode(
    identifier: IdentifierNode,
    genericInterfaces: List<GenericInterfaceDefinitionNode>,
    genericParameters: List<GenericParameterDefinitionNode>,
    val inherits: List<DefinedInterfaceExpressionNode>,
    val ports: List<RecordInterfacePortNode>,
): InterfaceDefinitionNode(identifier, genericInterfaces, genericParameters)
