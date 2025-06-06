package com.uabutler.util

import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.SignalInterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.StreamInterfaceTypeNode

enum class InterfaceType {
    SIGNAL,
    STREAM;

    companion object {
        fun fromInterfaceTypeNode(ioType: InterfaceTypeNode): InterfaceType {
            return when (ioType) {
                is DefaultInterfaceTypeNode, is SignalInterfaceTypeNode -> SIGNAL
                is StreamInterfaceTypeNode -> STREAM
            }
        }
    }
}