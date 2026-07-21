package com.uabutler.diagnostics

class DiagnosticsCollector {

    private val collected = mutableListOf<Diagnostic>()

    fun report(diagnostic: Diagnostic) {
        collected.add(diagnostic)
    }

    fun reportError(message: String, span: SourceSpan) {
        report(Diagnostic(Diagnostic.Severity.ERROR, message, span))
    }

    fun reportWarning(message: String, span: SourceSpan) {
        report(Diagnostic(Diagnostic.Severity.WARNING, message, span))
    }

    fun diagnostics(): List<Diagnostic> = collected.toList()

    fun hasErrors(): Boolean = collected.any { it.severity == Diagnostic.Severity.ERROR }

}
