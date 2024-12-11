package com.uabutler.ast

import com.uabutler.references.Scope

sealed interface GAPLNode

interface TemporaryNode: GAPLNode
interface PersistentNode: GAPLNode {
    var parent: PersistentNode?

    fun getScopeOfCurrentNode(): Scope? = null
    fun getScope(): Scope? = getScopeOfCurrentNode() ?: parent?.getScope()
}
