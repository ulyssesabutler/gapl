package com.uabutler.diagnostics

class DiagnosticsException(val diagnostics: List<Diagnostic>) : Exception(
    diagnostics.joinToString("\n") { it.toString() }
)
