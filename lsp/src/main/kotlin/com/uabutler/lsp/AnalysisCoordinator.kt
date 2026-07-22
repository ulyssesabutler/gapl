package com.uabutler.lsp

import com.uabutler.Analyzer
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.Diagnostic.Severity
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.resolver.scope.DefinitionLink
import com.uabutler.resolver.scope.SemanticToken
import com.uabutler.resolver.scope.SemanticTokenKind
import com.uabutler.util.Logger
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokenTypes
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

// The index of each entry here is the tokenType value encoded per-token below - order must stay
// in sync with SemanticTokenKind.lspTokenTypeIndex, and this exact list is what gets sent to the
// client as the SemanticTokensLegend in initialize().
val semanticTokenTypesLegend: List<String> = listOf(
    SemanticTokenTypes.Keyword,
    SemanticTokenTypes.Operator,
    SemanticTokenTypes.Number,
    SemanticTokenTypes.Function,
    SemanticTokenTypes.Interface,
    SemanticTokenTypes.Parameter,
    SemanticTokenTypes.Variable,
    SemanticTokenTypes.TypeParameter,
    SemanticTokenTypes.Comment,
)

private val SemanticTokenKind.lspTokenTypeIndex: Int
    get() = when (this) {
        SemanticTokenKind.KEYWORD -> 0
        SemanticTokenKind.OPERATOR -> 1
        SemanticTokenKind.NUMBER -> 2
        SemanticTokenKind.FUNCTION -> 3
        SemanticTokenKind.INTERFACE -> 4
        SemanticTokenKind.PARAMETER -> 5
        SemanticTokenKind.VARIABLE -> 6
        SemanticTokenKind.TYPE_PARAMETER -> 7
        SemanticTokenKind.COMMENT -> 8
    }

// LSP's semantic tokens wire format: a flat array, 5 ints per token, each token's position
// encoded relative to the previous token's start (not absolute) - deltaLine, deltaStartChar
// (relative to the previous token's column only when on the same line, else absolute), length,
// tokenType index, tokenModifiers bitmask (always 0 here - no modifiers implemented).
fun List<SemanticToken>.encodeSemanticTokens(): List<Int> {
    val sorted = sortedWith(compareBy({ it.span.startLine }, { it.span.startColumn }))
    val data = mutableListOf<Int>()
    var previousLine = 0
    var previousStartChar = 0

    for (token in sorted) {
        val line = token.span.startLine - 1 // LSP lines are 0-indexed, SourceSpan lines are 1-indexed
        val startChar = token.span.startColumn
        val length = token.span.endColumn - token.span.startColumn
        val deltaLine = line - previousLine
        val deltaStartChar = if (deltaLine == 0) startChar - previousStartChar else startChar

        data.add(deltaLine)
        data.add(deltaStartChar)
        data.add(length)
        data.add(token.kind.lspTokenTypeIndex)
        data.add(0)

        previousLine = line
        previousStartChar = startChar
    }

    return data
}

// Tracks the latest analysis per open document, so requests that don't carry the document's text
// (like textDocument/definition) have something to answer from.
object AnalysisCoordinator {

    private val definitionsByUri = ConcurrentHashMap<String, List<DefinitionLink>>()
    private val semanticTokensByUri = ConcurrentHashMap<String, List<SemanticToken>>()

    private data class AnalysisOutcome(
        val diagnostics: List<Diagnostic>,
        val definitions: List<DefinitionLink>,
        val semanticTokens: List<SemanticToken>,
    )

    private fun runAnalysis(uri: String, text: String): AnalysisOutcome = try {
        val result = Analyzer.analyzeFull(text)
        AnalysisOutcome(result.diagnostics, result.definitions, result.semanticTokens)
    } catch (e: DiagnosticsException) {
        AnalysisOutcome(e.diagnostics, emptyList(), emptyList())
    } catch (e: Exception) {
        Logger.error { "Analysis of $uri failed unexpectedly: ${e.stackTraceToString()}" }
        AnalysisOutcome(emptyList(), emptyList(), emptyList())
    }

    fun analyzeAndPublish(client: LanguageClient, uri: String, text: String) {
        val outcome = runAnalysis(uri, text)

        definitionsByUri[uri] = outcome.definitions
        semanticTokensByUri[uri] = outcome.semanticTokens
        client.publishDiagnostics(PublishDiagnosticsParams(uri, outcome.diagnostics.map { it.toLsp() }))
    }

    fun forget(uri: String) {
        definitionsByUri.remove(uri)
        semanticTokensByUri.remove(uri)
    }

    fun definitionAt(uri: String, position: Position): SourceSpan? {
        val definitions = definitionsByUri[uri] ?: return null
        return definitions.firstOrNull { it.usageSpan.contains(position) }?.declarationSpan
    }

    fun semanticTokensFor(uri: String): List<SemanticToken> = semanticTokensByUri[uri] ?: emptyList()
}
