package com.uabutler.netlistir.builder.util

import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.SourceSpan

// Carries a single Diagnostic out of a broken module instantiation attempt. Caught at the
// per-instantiation boundary in ModuleBuilder, which records the diagnostic and moves on to
// the next instantiation instead of aborting the whole compile.
class BuilderDiagnosticException(val diagnostic: Diagnostic) : Exception(diagnostic.message) {
    constructor(message: String, span: SourceSpan) : this(Diagnostic(Diagnostic.Severity.ERROR, message, span))
}
