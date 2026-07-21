package com.uabutler.diagnostics

class DiagnosticsCollector {

    private val collected = mutableListOf<Diagnostic>()

    fun report(diagnostic: Diagnostic) {
        collected.add(diagnostic)
    }

    fun reportError(kind: DiagnosticKind, span: SourceSpan) {
        report(Diagnostic(Diagnostic.Severity.ERROR, span, kind))
    }

    fun reportWarning(kind: DiagnosticKind, span: SourceSpan) {
        report(Diagnostic(Diagnostic.Severity.WARNING, span, kind))
    }

    fun diagnostics(): List<Diagnostic> = collected.toList()

    fun hasErrors(): Boolean = collected.any { it.severity == Diagnostic.Severity.ERROR }

}
