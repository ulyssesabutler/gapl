package com.uabutler

import com.uabutler.ast.ProgramNode
import com.uabutler.module.ModuleNodeRecordInterface
import com.uabutler.module.ModuleNodeTranslatableInterface
import com.uabutler.module.ModuleNodeVectorInterface
import com.uabutler.module.ModuleNodeWireInterface
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

fun test() {
    val a = ModuleNodeRecordInterface(
        mapOf(
            "a1" to ModuleNodeWireInterface(),
            "a2" to ModuleNodeWireInterface(),
        )
    )
    val test = ModuleNodeRecordInterface(
        mapOf(
            "b1" to a,
            "b2" to ModuleNodeVectorInterface(
                listOf(a, a, a, a),
            )
        )
    )

    println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(test))
}

// fun main(args: Array<String>) = compile(args)
fun main(args: Array<String>) = test()
