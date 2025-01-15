package com.uabutler.v1.visitor

import com.uabutler.parsers.generated.GAPLBaseVisitor
import com.uabutler.v1.ast.EmptyNode
import com.uabutler.v1.ast.GAPLNode

abstract class GAPLVisitor: GAPLBaseVisitor<GAPLNode>() {
    override fun defaultResult() = EmptyNode
}