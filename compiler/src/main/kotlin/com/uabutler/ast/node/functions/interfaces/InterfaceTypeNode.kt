package com.uabutler.ast.node.functions.interfaces

import com.uabutler.ast.node.PersistentNode

sealed interface InterfaceTypeNode: PersistentNode

data object DefaultInterfaceTypeNode: InterfaceTypeNode

data object StreamInterfaceTypeNode: InterfaceTypeNode

data object SignalInterfaceTypeNode: InterfaceTypeNode

