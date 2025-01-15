package com.uabutler.v1.ast

import com.uabutler.v1.references.Scope

interface ScopeNode: PersistentNode {
    var associatedScope: Scope?
    override fun getScopeOfCurrentNode(): Scope? = associatedScope
}