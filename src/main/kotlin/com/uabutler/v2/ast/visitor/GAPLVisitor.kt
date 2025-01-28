package com.uabutler.v2.ast.visitor

import com.uabutler.parsers.generated.GAPLBaseVisitor
import com.uabutler.v2.ast.node.EmptyNode
import com.uabutler.v2.ast.node.GAPLNode

abstract class GAPLVisitor: GAPLBaseVisitor<GAPLNode>() {
    override fun defaultResult() = EmptyNode
}