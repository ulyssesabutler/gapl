package com.uabutler.ast.node.functions.interfaces

import com.uabutler.ast.node.PersistentNode

sealed interface InterfaceTypeNode: PersistentNode

class DefaultInterfaceTypeNode: InterfaceTypeNode {
    override fun toString(): String = DefaultInterfaceTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is DefaultInterfaceTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class StreamInterfaceTypeNode: InterfaceTypeNode {
    override fun toString(): String = StreamInterfaceTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is StreamInterfaceTypeNode
    override fun hashCode() = javaClass.hashCode()
}

class SignalInterfaceTypeNode: InterfaceTypeNode {
    override fun toString(): String = SignalInterfaceTypeNode::class.java.simpleName
    override fun equals(other: Any?) = other is SignalInterfaceTypeNode
    override fun hashCode() = javaClass.hashCode()
}

