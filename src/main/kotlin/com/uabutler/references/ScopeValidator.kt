package com.uabutler.references

object ScopeValidator {
    fun validate(scope: Scope) {
        scope.allDeclarations()
            .sortedBy { it.identifier }
            .zipWithNext { a, b ->
                if (a.identifier == b.identifier) throw Exception("Duplicate declaration of ${a.identifier}")
            }

        scope.children().forEach { validate(it) }
    }
}