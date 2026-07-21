package com.uabutler.diagnostics

data class Diagnostic(
    val severity: Severity,
    val span: SourceSpan,
    val kind: DiagnosticKind,
    val note: String? = null,
) {
    enum class Severity { ERROR, WARNING }

    val message: String get() = kind.message + (note?.let { " ($it)" } ?: "")

    override fun toString() = "${severity.name.lowercase()} at $span: $message"

    fun shiftedLines(delta: Int) = copy(span = span.shiftedLines(delta))
}
