package com.uabutler.resolver.scope

import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.util.PredefinedFunctionNames
import org.antlr.v4.kotlinruntime.Token

data class DeclaredSymbol(val name: String, val token: Token)

interface Scope {

    val parentScope: Scope?

    // Every scope but the root delegates to its parent, so only ProgramScope needs to own one.
    val diagnostics: DiagnosticsCollector get() = parentScope!!.diagnostics

    fun resolveLocal(name: String): ResolvedSymbol?

    fun resolveGlobal(name: String): ResolvedSymbol? {
        return resolveLocal(name) ?: parentScope?.resolveGlobal(name)
    }

    fun resolve(identifier: Token): ResolvedSymbol? {
        val name = identifier.text!!

        if (name in predefinedFunctionNames) return ResolvedSymbol.Function(ctx = null)

        return resolveGlobal(name) ?: run {
            diagnostics.reportError(ResolverDiagnosticKind.UnresolvedSymbol(name), SourceSpan.of(identifier))
            null
        }
    }

    fun symbols(): List<DeclaredSymbol>

    fun validateSymbols() {
        val seen = mutableSetOf<String>()

        symbols().forEach { symbol ->
            if (symbol.name in seen || symbol.name in predefinedFunctionNames) {
                diagnostics.reportError(ResolverDiagnosticKind.Redeclaration(symbol.name), SourceSpan.of(symbol.token))
            } else {
                seen.add(symbol.name)
            }
        }
    }

    companion object {
        private val predefinedFunctionNames = PredefinedFunctionNames.entries.map { it.gaplName }.toSet()
    }

}
