package com.uabutler.ast

import com.uabutler.references.Scope

interface ScopeNode: PersistentNode {
    var associatedScope: Scope?
    override fun getScopeOfCurrentNode(): Scope? = associatedScope
}