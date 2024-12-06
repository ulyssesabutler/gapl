package com.uabutler

import com.uabutler.ast.ProgramNode
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: gapl [filename]")
        exitProcess(1)
    }

    val parser = Parser.fromFileName(args.first())
    val ast = ProgramNode.fromParser(parser)

    ast.interfaces.forEach { println(it) }
}
