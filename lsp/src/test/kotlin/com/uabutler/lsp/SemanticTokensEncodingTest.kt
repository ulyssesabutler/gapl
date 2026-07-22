package com.uabutler.lsp

import com.uabutler.diagnostics.SourceSpan
import com.uabutler.resolver.scope.SemanticToken
import com.uabutler.resolver.scope.SemanticTokenKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticTokensEncodingTest {

    @Test
    fun `a single token encodes as an absolute position with zero deltas`() {
        val tokens = listOf(SemanticToken(SourceSpan(startLine = 1, startColumn = 0, endLine = 1, endColumn = 8), SemanticTokenKind.KEYWORD))

        // deltaLine=0, deltaStartChar=0 (first token, relative to the implicit (0,0) origin), length=8, type index 0 (Keyword), no modifiers.
        assertEquals(listOf(0, 0, 8, 0, 0), tokens.encodeSemanticTokens())
    }

    @Test
    fun `two tokens on the same line encode the second relative to the first`() {
        val tokens = listOf(
            SemanticToken(SourceSpan(startLine = 1, startColumn = 0, endLine = 1, endColumn = 8), SemanticTokenKind.KEYWORD),
            SemanticToken(SourceSpan(startLine = 1, startColumn = 9, endLine = 1, endColumn = 12), SemanticTokenKind.FUNCTION),
        )

        assertEquals(
            listOf(
                0, 0, 8, 0, 0, // "function" at (0,0), length 8, Keyword
                0, 9, 3, 3, 0, // "top" at (0,9), deltaStartChar = 9-0 = 9, length 3, Function
            ),
            tokens.encodeSemanticTokens(),
        )
    }

    @Test
    fun `a token on a new line resets deltaStartChar to an absolute column`() {
        val tokens = listOf(
            SemanticToken(SourceSpan(startLine = 1, startColumn = 0, endLine = 1, endColumn = 8), SemanticTokenKind.KEYWORD),
            SemanticToken(SourceSpan(startLine = 2, startColumn = 4, endLine = 2, endColumn = 5), SemanticTokenKind.PARAMETER),
        )

        assertEquals(
            listOf(
                0, 0, 8, 0, 0,
                1, 4, 1, 5, 0, // deltaLine = 1, deltaStartChar = absolute column 4 (not relative, since the line changed)
            ),
            tokens.encodeSemanticTokens(),
        )
    }

    @Test
    fun `tokens are sorted by position before encoding, regardless of insertion order`() {
        val tokens = listOf(
            SemanticToken(SourceSpan(startLine = 2, startColumn = 0, endLine = 2, endColumn = 1), SemanticTokenKind.VARIABLE),
            SemanticToken(SourceSpan(startLine = 1, startColumn = 0, endLine = 1, endColumn = 1), SemanticTokenKind.KEYWORD),
        )

        val data = tokens.encodeSemanticTokens()
        // First emitted token must be the one on line 1 (deltaLine 0 from origin), not insertion order.
        assertEquals(0, data[0])
    }
}
