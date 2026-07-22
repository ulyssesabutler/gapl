package com.uabutler.resolver.scope

import com.uabutler.diagnostics.SourceSpan

enum class SemanticTokenKind {
    KEYWORD, OPERATOR, NUMBER, FUNCTION, INTERFACE, PARAMETER, VARIABLE, TYPE_PARAMETER, COMMENT
}

data class SemanticToken(val span: SourceSpan, val kind: SemanticTokenKind)

class SemanticTokensCollector {

    private val tokens = mutableListOf<SemanticToken>()

    fun record(span: SourceSpan, kind: SemanticTokenKind) {
        tokens.add(SemanticToken(span, kind))
    }

    fun tokens(): List<SemanticToken> = tokens.toList()

}
