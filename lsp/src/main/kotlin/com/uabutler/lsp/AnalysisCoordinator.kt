package com.uabutler.lsp

import com.uabutler.Analyzer
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.Diagnostic.Severity
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.resolver.scope.DefinitionLink
import com.uabutler.util.Logger
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.ConcurrentHashMap

fun SourceSpan.toLspRange(): Range = Range(
    Position(startLine - 1, startColumn),
    Position(endLine - 1, endColumn),
)

// Identifier spans are always single-token/single-line, so containment is just a same-line +
// column-range check, not general multi-line span logic.
fun SourceSpan.contains(position: Position): Boolean {
    val line = position.line + 1
    return line == startLine && position.character >= startColumn && position.character < endColumn
}

fun Diagnostic.toLsp(): org.eclipse.lsp4j.Diagnostic = org.eclipse.lsp4j.Diagnostic(
    span.toLspRange(),
    message,
    when (severity) {
        Severity.ERROR -> DiagnosticSeverity.Error
        Severity.WARNING -> DiagnosticSeverity.Warning
    },
    "gapl",
)

// Tracks the latest analysis per open document, so requests that don't carry the document's text
// (like textDocument/definition) have something to answer from.
object AnalysisCoordinator {

    private val definitionsByUri = ConcurrentHashMap<String, List<DefinitionLink>>()

    private data class AnalysisOutcome(val diagnostics: List<Diagnostic>, val definitions: List<DefinitionLink>)

    private fun runAnalysis(uri: String, text: String): AnalysisOutcome = try {
        val result = Analyzer.analyzeFull(text)
        AnalysisOutcome(result.diagnostics, result.definitions)
    } catch (e: DiagnosticsException) {
        AnalysisOutcome(e.diagnostics, emptyList())
    } catch (e: Exception) {
        Logger.error { "Analysis of $uri failed unexpectedly: ${e.stackTraceToString()}" }
        AnalysisOutcome(emptyList(), emptyList())
    }

    fun analyzeAndPublish(client: LanguageClient, uri: String, text: String) {
        val outcome = runAnalysis(uri, text)

        definitionsByUri[uri] = outcome.definitions
        client.publishDiagnostics(PublishDiagnosticsParams(uri, outcome.diagnostics.map { it.toLsp() }))
    }

    fun forget(uri: String) {
        definitionsByUri.remove(uri)
    }

    fun definitionAt(uri: String, position: Position): SourceSpan? {
        val definitions = definitionsByUri[uri] ?: return null
        return definitions.firstOrNull { it.usageSpan.contains(position) }?.declarationSpan
    }
}
