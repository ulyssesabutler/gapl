package com.uabutler.ast.visitor

import com.uabutler.parsers.generated.GAPLBaseVisitor
import com.uabutler.ast.node.EmptyNode
import com.uabutler.ast.node.GAPLNode

abstract class GAPLVisitor: GAPLBaseVisitor<GAPLNode>() {
    override fun defaultResult() = EmptyNode
}