package com.uabutler

import com.uabutler.ast.ProgramNode
import com.uabutler.module.*
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
    val program = """
        interface sub_payload()
        {
            first: wire[8];
            second: wire[8];
        }
        
        interface payload()
        {
            a: sub_payload();
            b: sub_payload();
        }
        
        interface data_stream()
        {
            data: payload()[8];
            metadata: wire[16];
        }
        
        function sub() i: data_stream() => o: data_stream()
        {
            i => o;
        }
        
        function test() i: data_stream() => o: data_stream() 
        {
            t: data_stream();
            i => sub() => t;
            t => o;
        }
    """.trimIndent()

    val parser = Parser.fromString(program)
    val ast = ProgramNode.fromParser(parser)
    val moduleBuilder = ModuleBuilder.fromAST(ast)
    moduleBuilder.concreteModules.forEach {
        println("MODULE:")
        println(it)

        println("INPUT:")
        it.inputNodes.forEach { inputNode ->
            println("  Input Outputs:")
            inputNode.value.output.forEach { output ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(output.identifier, output))
            }
        }

        println("OUTPUT:")
        it.outputNodes.forEach { outputNode ->
            println("  Output Inputs:")
            outputNode.value.input.forEach { input ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input))
            }
        }

        println("BODY:")
        it.bodyNodes.forEach { bodyNode ->
            println("  Body Node Inputs:")
            bodyNode.value.input.forEach { input ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input))
            }
            println("  Body Node Outputs:")
            bodyNode.value.output.forEach { output ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(output.identifier, output))
            }
        }

        println("ANONYMOUS:")
        it.anonymousNodes.forEach { anonymousNode ->
            println("  Anonymous Node Inputs:")
            anonymousNode.value.input.forEach { input ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input))
            }
            println("  Anonymous Node Outputs:")
            anonymousNode.value.output.forEach { output ->
                println(ModuleNodeTranslatableInterface.fromModuleNodeInterface(output.identifier, output))
            }
        }
    }

}

// fun main(args: Array<String>) = compile(args)
fun main(args: Array<String>) = test()
