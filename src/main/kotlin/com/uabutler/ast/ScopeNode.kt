package com.uabutler.ast

import com.uabutler.references.Scope

interface ScopeNode {
    var associatedScope: Scope?
}