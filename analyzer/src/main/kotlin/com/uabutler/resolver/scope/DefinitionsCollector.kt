package com.uabutler.resolver.scope

import com.uabutler.diagnostics.SourceSpan

data class DefinitionLink(val usageSpan: SourceSpan, val declarationSpan: SourceSpan)

class DefinitionsCollector {

    private val links = mutableListOf<DefinitionLink>()

    fun record(usageSpan: SourceSpan, declarationSpan: SourceSpan) {
        links.add(DefinitionLink(usageSpan, declarationSpan))
    }

    fun definitions(): List<DefinitionLink> = links.toList()

}
