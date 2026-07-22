package com.uabutler.lsp

import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.diagnostics.SyntaxDiagnosticKind
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsConverterTest {

    @Test
    fun `span converts from 1-indexed lines to 0-indexed lsp positions, columns pass through`() {
        val span = SourceSpan(startLine = 3, startColumn = 4, endLine = 3, endColumn = 9)

        val range = span.toLspRange()

        assertEquals(Position(2, 4), range.start)
        assertEquals(Position(2, 9), range.end)
    }

    @Test
    fun `multi-line span converts both line endpoints independently`() {
        val span = SourceSpan(startLine = 1, startColumn = 0, endLine = 5, endColumn = 2)

        val range = span.toLspRange()

        assertEquals(Position(0, 0), range.start)
        assertEquals(Position(4, 2), range.end)
    }

    @Test
    fun `error severity and message convert to the lsp diagnostic`() {
        val diagnostic = Diagnostic(
            severity = Diagnostic.Severity.ERROR,
            span = SourceSpan(1, 0, 1, 5),
            kind = SyntaxDiagnosticKind.SyntaxError("unexpected token"),
        )

        val lsp = diagnostic.toLsp()

        assertEquals("unexpected token", lsp.message)
        assertEquals(DiagnosticSeverity.Error, lsp.severity)
        assertEquals("gapl", lsp.source)
        assertEquals(Position(0, 0), lsp.range.start)
    }

    @Test
    fun `warning severity converts to lsp warning`() {
        val diagnostic = Diagnostic(
            severity = Diagnostic.Severity.WARNING,
            span = SourceSpan(1, 0, 1, 5),
            kind = SyntaxDiagnosticKind.SyntaxError("just a warning"),
        )

        assertEquals(DiagnosticSeverity.Warning, diagnostic.toLsp().severity)
    }
}
