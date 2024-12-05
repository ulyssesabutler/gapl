package com.uabutler.visitor

import com.uabutler.ast.EmptyNode
import com.uabutler.ast.GAPLNode
import com.uabutler.parsers.generated.GAPLBaseVisitor

class GAPLProgramVisitor: GAPLBaseVisitor<GAPLNode>() {
    override fun defaultResult(): GAPLNode {
        return EmptyNode()
    }
}