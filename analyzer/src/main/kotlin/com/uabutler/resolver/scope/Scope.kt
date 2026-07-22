package com.uabutler.resolver.scope

import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.util.PredefinedFunctionNames
import org.antlr.v4.kotlinruntime.Token

data class DeclaredSymbol(val name: String, val token: Token)

fun semanticTokenKind(resolved: ResolvedSymbol): SemanticTokenKind = when (resolved) {
    is ResolvedSymbol.Function -> SemanticTokenKind.FUNCTION
    is ResolvedSymbol.Interface -> SemanticTokenKind.INTERFACE
    is ResolvedSymbol.Parameter -> SemanticTokenKind.TYPE_PARAMETER
    is ResolvedSymbol.CircuitNode -> SemanticTokenKind.VARIABLE
    is ResolvedSymbol.FunctionIO -> SemanticTokenKind.PARAMETER
}

interface Scope {

    val parentScope: Scope?

    // Every scope but the root delegates to its parent, so only ProgramScope needs to own one.
    val diagnostics: DiagnosticsCollector get() = parentScope!!.diagnostics
    val definitions: DefinitionsCollector get() = parentScope!!.definitions
    val semanticTokens: SemanticTokensCollector get() = parentScope!!.semanticTokens

    fun resolveLocal(name: String): ResolvedSymbol?

    fun resolveGlobal(name: String): ResolvedSymbol? {
        return resolveLocal(name) ?: parentScope?.resolveGlobal(name)
    }

    fun resolve(identifier: Token): ResolvedSymbol? {
        val name = identifier.text!!

        if (name in predefinedFunctionNames) return ResolvedSymbol.Function(ctx = null)

        val resolved = resolveGlobal(name)
        if (resolved == null) {
            diagnostics.reportError(ResolverDiagnosticKind.UnresolvedSymbol(name), SourceSpan.of(identifier))
            return null
        }

        val usageSpan = SourceSpan.of(identifier)
        declarationSpan(resolved)?.let { definitions.record(usageSpan, it) }
        semanticTokens.record(usageSpan, semanticTokenKind(resolved))

        return resolved
    }

    private fun declarationSpan(resolved: ResolvedSymbol): SourceSpan? = when (resolved) {
        is ResolvedSymbol.Function -> resolved.ctx?.let { SourceSpan.of(it) }
        is ResolvedSymbol.Interface -> SourceSpan.of(resolved.ctx)
        is ResolvedSymbol.Parameter -> SourceSpan.of(resolved.ctx)
        is ResolvedSymbol.CircuitNode -> SourceSpan.of(resolved.ctx)
        is ResolvedSymbol.FunctionIO -> SourceSpan.of(resolved.ctx)
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
