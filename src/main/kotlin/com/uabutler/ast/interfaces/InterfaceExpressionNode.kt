package com.uabutler.ast.interfaces

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode

sealed class InterfaceExpressionNode: PersistentNode()

class WireInterfaceExpressionNode: InterfaceExpressionNode()

class DefinedInterfaceExpressionNode: InterfaceExpressionNode()

class VectorInterfaceExpressionNode: InterfaceExpressionNode()

class IdentifierInterfaceExpressionNode(val interfaceIdentifier: IdentifierNode): InterfaceExpressionNode()