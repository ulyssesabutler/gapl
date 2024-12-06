package com.uabutler.ast.interfaces

import com.uabutler.ast.PersistentNode
import com.uabutler.ast.TemporaryNode
import com.uabutler.ast.staticexpressions.StaticExpressionNode

data class GenericInterfaceValueNode(val value: InterfaceExpressionNode): PersistentNode
data class GenericParameterValueNode(val value: StaticExpressionNode): PersistentNode

data class GenericInterfaceValueListNode(val interfaces: List<GenericInterfaceValueNode>): TemporaryNode
data class GenericParameterValueListNode(val parameters: List<GenericParameterValueNode>): TemporaryNode

data class VectorBoundsNode(val boundSpecifier: StaticExpressionNode): PersistentNode