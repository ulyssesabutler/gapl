package com.uabutler.diagnostics

class DiagnosticsCollector {

    // A LinkedHashSet (not a List) so an exact repeat - same severity, span, and kind (every
    // DiagnosticKind implementation is a data class, so this is real structural equality, not just
    // "same type") - is silently dropped instead of shown twice, while preserving report order for
    // everything else. This matters for checks that can independently rediscover the same textual
    // bug more than once, e.g. a combinational loop inside a generic function gets re-detected once
    // per otherwise-unrelated generic instantiation (round_key(1), round_key(2), ... in aes/test.gapl
    // all report the identical bug at the identical declaration span) - two diagnostics that are
    // genuinely different always differ in kind content (different port/node names, etc.) and so
    // never collide here by accident.
    private val collected = linkedSetOf<Diagnostic>()

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
