package com.uabutler.diagnostics

// What kind of problem a Diagnostic reports, independent of its display text - lets tests assert
// on `diagnostic.kind is DiagnosticKind.X` instead of matching message strings, so wording can be
// changed freely without touching tests.
sealed interface DiagnosticKind {
    val message: String
}

sealed interface SyntaxDiagnosticKind : DiagnosticKind {
    // ANTLR's syntax-error messages come pre-formatted with no further structure to extract.
    data class SyntaxError(val rawMessage: String) : SyntaxDiagnosticKind {
        override val message get() = rawMessage
    }
}
