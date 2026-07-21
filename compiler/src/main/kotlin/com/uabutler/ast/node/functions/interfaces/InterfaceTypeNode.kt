package com.uabutler.ast.node.functions.interfaces

import com.uabutler.ast.node.GAPLNode
import com.uabutler.diagnostics.SourceSpan

sealed interface InterfaceTypeNode: GAPLNode

data class DefaultInterfaceTypeNode(override val span: SourceSpan): InterfaceTypeNode

data class StreamInterfaceTypeNode(override val span: SourceSpan): InterfaceTypeNode

data class SignalInterfaceTypeNode(override val span: SourceSpan): InterfaceTypeNode
