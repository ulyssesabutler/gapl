package com.uabutler

import com.uabutler.cst.visitor.CSTProgramVisitor
import com.uabutler.cst.visitor.functions.CSTFunctionDefinitionVisitor
import com.uabutler.cst.visitor.interfaces.CSTInterfaceDefinitionVisitor
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

    // The hand-rolled CST visitors assume a well-formed parse tree, so they must never run
    // on a tree ANTLR's error recovery produced from invalid input - guard on diagnostics
    // right after each raw parse, before handing the tree off to the visitor.
    private fun <T> guarded(parse: () -> T): T {
        val tree = parse()
        if (diagnostics.hasErrors()) throw DiagnosticsException(diagnostics.diagnostics())
        return tree
    }

    fun program() = CSTProgramVisitor.visitProgram(guarded { parseTree.value.program() })

    fun functionDefinition() = CSTFunctionDefinitionVisitor.visitFunctionDefinition(guarded { parseTree.value.functionDefinition() })

    fun interfaceDefinition() = CSTInterfaceDefinitionVisitor.visitInterfaceDefinition(guarded { parseTree.value.interfaceDefinition() })

}