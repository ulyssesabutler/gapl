package com.uabutler

import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.diagnostics.ParserErrorListener
import com.uabutler.parsers.generated.CSTLexer
import com.uabutler.parsers.generated.CSTParser
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

class Parser private constructor(private val characterStream: CharStream) {

    val diagnostics = DiagnosticsCollector()

    private val parseTree = lazy {
        val errorListener = ParserErrorListener(diagnostics)

        val lexer = CSTLexer(characterStream).also {
            it.removeErrorListeners()
            it.addErrorListener(errorListener)
        }

        CSTParser(CommonTokenStream(lexer)).also {
            it.removeErrorListeners()
            it.addErrorListener(errorListener)
        }
    }

    companion object {
        fun fromString(input: String) = Parser(CharStreams.fromString(input))
    }

    // The analyzer assumes a well-formed parse tree, so it must never run on a tree ANTLR's
    // error recovery produced from invalid input - guard on diagnostics right after each raw
    // parse, before handing the tree off for analysis.
    private fun <T> guarded(parse: () -> T): T {
        val tree = parse()
        if (diagnostics.hasErrors()) throw DiagnosticsException(diagnostics.diagnostics())
        return tree
    }

    fun program() = guarded { parseTree.value.program() }

    fun functionDefinition() = guarded { parseTree.value.functionDefinition() }

    fun interfaceDefinition() = guarded { parseTree.value.interfaceDefinition() }

}
