package com.uabutler.visitor

import com.uabutler.ast.*
import com.uabutler.parsers.generated.GAPLBaseVisitor

abstract class GAPLVisitor: GAPLBaseVisitor<GAPLNode>() {
    override fun defaultResult() = EmptyNode
}