package com.uabutler

import com.uabutler.ast.ProgramNode
import com.uabutler.references.IdentifierMap
import com.uabutler.references.Scope
import com.uabutler.references.ScopeValidator
import kotlin.system.exitProcess

fun compile(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: gapl [filename]")
        exitProcess(1)
    }

    val parser = Parser.fromFileName(args.first())
    val ast = ProgramNode.fromParser(parser)

    ast.interfaces.forEach { println(it) }
}

fun main(args: Array<String>) = compile(args)
