package com.uabutler

import com.uabutler.visitor.ASTVisitor
import com.uabutler.parsers.generated.*
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.CharStream

fun parse(characterStream: CharStream) =
    characterStream
        .let { GAPLLexer(it) }
        .let { GAPLParser(CommonTokenStream(it)) }
        .program()

fun main() {
    val input = """
        interface test() wire
        interface test() { }
        interface test() { a: wire; }
        interface test(): parent() { }
    """.trimIndent()

    val charStream = CharStreams.fromString(input)
    val parseTree = parse(charStream)
    val ast = ASTVisitor().visitProgram(parseTree)

    ast.interfaces.forEach { println(it) }
}
