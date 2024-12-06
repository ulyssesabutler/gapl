package com.uabutler

import com.uabutler.ast.ProgramNode

fun main() {
    val input = """
        interface test() wire
        interface test() { }
        interface test() { a: wire; }
        interface test(): parent() { }
    """.trimIndent()

    val parser = Parser.fromString(input)
    val ast = ProgramNode.fromParser(parser)

    ast.interfaces.forEach { println(it) }
}
