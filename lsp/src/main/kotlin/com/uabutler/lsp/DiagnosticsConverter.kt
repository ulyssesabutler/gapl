package com.uabutler.lsp

import com.uabutler.Analyzer
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.diagnostics.SourceSpan
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient
import com.uabutler.diagnostics.Diagnostic.Severity
import com.uabutler.util.Logger
import org.eclipse.lsp4j.DiagnosticSeverity

fun SourceSpan.toLspRange(): Range = Range(
    Position(startLine - 1, startColumn),
    Position(endLine - 1, endColumn),
)

fun Diagnostic.toLsp(): org.eclipse.lsp4j.Diagnostic = org.eclipse.lsp4j.Diagnostic(
    span.toLspRange(),
    message,
    when (severity) {
        Severity.ERROR -> DiagnosticSeverity.Error
        Severity.WARNING -> DiagnosticSeverity.Warning
    },
    "gapl",
)

object DiagnosticsConverter {

    fun analyzeAndPublish(client: LanguageClient, uri: String, text: String) {
        val diagnostics = try {
            Analyzer.analyzeFull(text).diagnostics
        } catch (e: DiagnosticsException) {
            e.diagnostics
        } catch (e: Exception) {
            Logger.error { "Analysis of $uri failed unexpectedly: ${e.stackTraceToString()}" }
            emptyList()
        }

        client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics.map { it.toLsp() }))
    }
}
