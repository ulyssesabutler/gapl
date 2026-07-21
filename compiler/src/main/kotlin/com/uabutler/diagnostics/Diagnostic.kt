package com.uabutler.diagnostics

data class Diagnostic(
    val severity: Severity,
    val message: String,
    val span: SourceSpan,
) {
    enum class Severity { ERROR, WARNING }

    override fun toString() = "${severity.name.lowercase()} at $span: $message"
}
