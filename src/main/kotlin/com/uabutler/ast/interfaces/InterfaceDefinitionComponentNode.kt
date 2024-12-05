package com.uabutler.ast.interfaces

import com.uabutler.ast.IdentifierNode

data class GenericInterfaceDefinitionNode(val identifier: IdentifierNode)
data class GenericParameterDefinitionNode(val identifier: IdentifierNode, val typeIdentifier: IdentifierNode)

data class RecordInterfacePortNode(val identifier: IdentifierNode, val type: InterfaceExpressionNode)