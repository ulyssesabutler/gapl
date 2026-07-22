package com.uabutler.lsp

import com.uabutler.diagnostics.SourceSpan
import org.eclipse.lsp4j.Position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSpanContainsTest {

    // A span covering columns [4, 9) on line 3 (1-indexed).
    private val span = SourceSpan(startLine = 3, startColumn = 4, endLine = 3, endColumn = 9)

    @Test
    fun `position inside the column range on the right line is contained`() {
        assertTrue(span.contains(Position(2, 4)))
        assertTrue(span.contains(Position(2, 8)))
    }

    @Test
    fun `position at the exclusive end column is not contained`() {
        assertFalse(span.contains(Position(2, 9)))
    }

    @Test
    fun `position just before the start column is not contained`() {
        assertFalse(span.contains(Position(2, 3)))
    }

    @Test
    fun `position on a different line is not contained`() {
        assertFalse(span.contains(Position(3, 5)))
        assertFalse(span.contains(Position(1, 5)))
    }
}
