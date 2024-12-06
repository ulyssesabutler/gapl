package com.uabutler.ast.interfaces

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode

data class GenericInterfaceDefinitionNode(val identifier: IdentifierNode): PersistentNode
data class GenericParameterDefinitionNode(val identifier: IdentifierNode, val typeIdentifier: IdentifierNode): PersistentNode

data class GenericInterfaceDefinitionListNode(val interfaces: List<GenericInterfaceDefinitionNode>): TemporaryNode
data class GenericParameterDefinitionListNode(val parameters: List<GenericParameterDefinitionNode>): TemporaryNode

data class RecordInterfacePortNode(val identifier: IdentifierNode, val type: InterfaceExpressionNode): PersistentNode

data class RecordInterfaceInheritListNode(val inherits: List<DefinedInterfaceExpressionNode>): TemporaryNode
data class RecordInterfacePortListNode(val ports: List<RecordInterfacePortNode>): TemporaryNode