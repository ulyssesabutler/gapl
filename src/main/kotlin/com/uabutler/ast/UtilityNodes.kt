package com.uabutler.ast

import com.uabutler.ast.interfaces.GenericInterfaceValueNode
import com.uabutler.ast.interfaces.GenericParameterValueNode

data object EmptyNode: TemporaryNode

data class InstantiationNode(
    val definitionIdentifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceValueNode>,
    val genericParameters: List<GenericParameterValueNode>,
): TemporaryNode