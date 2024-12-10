package com.uabutler

import com.uabutler.parsers.generated.GAPLLexer
import com.uabutler.parsers.generated.GAPLParser
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

class Parser private constructor(private val characterStream: CharStream) {

    private val parseTree = lazy {
        characterStream
            .let { GAPLLexer(it) }
            .let { GAPLParser(CommonTokenStream(it)) }
    }

    companion object {
        fun fromString(input: String) = Parser(CharStreams.fromString(input))
        fun fromFileName(input: String) = Parser(CharStreams.fromFileName(input))
    }

    fun program() = parseTree.value.program()

    fun interfaceDefinition() = parseTree.value.interfaceDefinition()
    fun functionDefinition() = parseTree.value.functionDefinition()

    fun staticExpression() = parseTree.value.staticExpression()
}