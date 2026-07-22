package com.uabutler

import com.uabutler.ast.node.ProgramNode
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.netlistir.builder.ModuleBuilder
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.parsers.generated.CSTLexer
import com.uabutler.resolver.Resolver
import com.uabutler.resolver.scope.DefinitionLink
import com.uabutler.resolver.scope.SemanticToken
import com.uabutler.resolver.scope.SemanticTokenKind
import com.uabutler.util.Logger
import com.uabutler.util.standardLibary
import org.antlr.v4.kotlinruntime.CharStreams

object Analyzer {

    data class Options(val includeStdLib: Boolean = true)

    fun preprocess(gapl: String, options: Options) = buildString {
        if (options.includeStdLib) appendLine(standardLibary)
        appendLine(gapl)
    }

    // The number of lines occupied by the prepended standard library in the preprocessed source,
    // used to translate diagnostic spans back to the user's original source before reporting them.
    fun stdLibLineOffset(options: Options) = if (options.includeStdLib) standardLibary.count { it == '\n' } + 1 else 0

    // Diagnostics are collected against the preprocessed (stdlib-prepended) source; shift them back
    // to the user's own source before they're ever reported, flagging the rare case where an error
    // genuinely falls inside the prepended stdlib text as a likely compiler bug instead of showing
    // a nonsensical negative line number.
    fun shiftToUserSource(diagnostics: List<Diagnostic>, options: Options): List<Diagnostic> {
        val offset = stdLibLineOffset(options)

        return diagnostics.map { diagnostic ->
            if (diagnostic.span.startLine - offset <= 0) {
                diagnostic.copy(note = "this location is inside the prepended standard library, not your source - if you did not modify the standard library, this is likely a compiler bug; please contact a TA")
            } else {
                diagnostic.shiftedLines(-offset)
            }
        }
    }

    // Declarations that fall inside the prepended stdlib text (e.g. jumping to a builtin like
    // `register`) are dropped rather than shifted to a bogus negative line - there's no real file
    // the LSP can point an editor at for text that's invisible to the user.
    fun shiftDefinitionsToUserSource(definitions: List<DefinitionLink>, options: Options): List<DefinitionLink> {
        val offset = stdLibLineOffset(options)

        return definitions.mapNotNull { link ->
            if (link.declarationSpan.startLine - offset <= 0) {
                null
            } else {
                DefinitionLink(link.usageSpan.shiftedLines(-offset), link.declarationSpan.shiftedLines(-offset))
            }
        }
    }

    // Declarations that fall inside the prepended stdlib text are dropped the same way
    // shiftDefinitionsToUserSource drops them - no real file to point an editor's decoration at.
    fun shiftSemanticTokensToUserSource(tokens: List<SemanticToken>, options: Options): List<SemanticToken> {
        val offset = stdLibLineOffset(options)

        return tokens.mapNotNull { token ->
            if (token.span.startLine - offset <= 0) {
                null
            } else {
                token.copy(span = token.span.shiftedLines(-offset))
            }
        }
    }

    // Keywords/operators/numbers never make it into the AST (only identifier spans do), so they
    // need their own lex pass - Parser doesn't expose its internal lexer/token stream, and this is
    // cheap enough that re-lexing independently is simpler than threading one through.
    private fun lexicalTokens(text: String): List<SemanticToken> {
        val lexer = CSTLexer(CharStreams.fromString(text))

        return lexer.allTokens.mapNotNull { token ->
            val kind = when (token.type) {
                CSTLexer.Tokens.Interface, CSTLexer.Tokens.Wire, CSTLexer.Tokens.Function, CSTLexer.Tokens.Declare,
                CSTLexer.Tokens.If, CSTLexer.Tokens.Else, CSTLexer.Tokens.Null, CSTLexer.Tokens.True, CSTLexer.Tokens.False,
                CSTLexer.Tokens.In, CSTLexer.Tokens.Out, CSTLexer.Tokens.Inout, CSTLexer.Tokens.Integer -> SemanticTokenKind.KEYWORD

                CSTLexer.Tokens.Add, CSTLexer.Tokens.Subtract, CSTLexer.Tokens.Multiply, CSTLexer.Tokens.Divide,
                CSTLexer.Tokens.Remainder, CSTLexer.Tokens.Equals, CSTLexer.Tokens.NotEquals,
                CSTLexer.Tokens.GreaterThanEquals, CSTLexer.Tokens.LessThanEquals, CSTLexer.Tokens.Connector,
                CSTLexer.Tokens.LogicalAnd, CSTLexer.Tokens.LogicalOr -> SemanticTokenKind.OPERATOR

                CSTLexer.Tokens.IntLiteral -> SemanticTokenKind.NUMBER

                else -> null
            }

            kind?.let { SemanticToken(SourceSpan.of(token), it) }
        }
    }

    fun analyze(gapl: String, options: Options = Options()): Resolver.AnalysisResult {
        val preprocessed = Logger.run("Preprocessing", Logger.Level.INFO) { preprocess(gapl, options) }

        val program = try {
            Logger.run("Parser", Logger.Level.INFO) { Parser.fromString(preprocessed).program() }
        } catch (e: DiagnosticsException) {
            throw DiagnosticsException(shiftToUserSource(e.diagnostics, options))
        }

        val result = Logger.run("Resolver", Logger.Level.INFO) { Resolver.analyze(program) }
        val semanticTokens = result.semanticTokens + lexicalTokens(preprocessed)

        return result.copy(
            diagnostics = shiftToUserSource(result.diagnostics, options),
            definitions = shiftDefinitionsToUserSource(result.definitions, options),
            semanticTokens = shiftSemanticTokensToUserSource(semanticTokens, options),
        )
    }

    data class FullAnalysisResult(
        val ast: ProgramNode,
        val diagnostics: List<Diagnostic>,
        val definitions: List<DefinitionLink>,
        val semanticTokens: List<SemanticToken>,
        // Null when resolver diagnostics blocked netlist-building.
        val modules: List<MutableModule>?,
    )

    // Parses, resolves, and builds the netlist IR, so diagnostics from ModuleBuilder (undriven
    // wires, width/arity mismatches) are included alongside syntax/resolver diagnostics.
    fun analyzeFull(gapl: String, options: Options = Options()): FullAnalysisResult {
        val analysis = analyze(gapl, options)

        if (analysis.diagnostics.isNotEmpty()) {
            return FullAnalysisResult(analysis.ast, analysis.diagnostics, analysis.definitions, analysis.semanticTokens, null)
        }

        val netlistResult = Logger.run("Netlist Builder", Logger.Level.INFO) { ModuleBuilder(analysis.ast).buildAllModules() }
        val diagnostics = analysis.diagnostics + shiftToUserSource(netlistResult.diagnostics, options)

        return FullAnalysisResult(analysis.ast, diagnostics, analysis.definitions, analysis.semanticTokens, netlistResult.modules)
    }

}
