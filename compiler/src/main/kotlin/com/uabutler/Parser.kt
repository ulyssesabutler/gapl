package com.uabutler

import com.uabutler.cst.visitor.CSTProgramVisitor
import com.uabutler.cst.visitor.functions.CSTFunctionDefinitionVisitor
import com.uabutler.cst.visitor.interfaces.CSTInterfaceDefinitionVisitor
import com.uabutler.parsers.generated.CSTLexer
import com.uabutler.parsers.generated.CSTParser
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

class Parser private constructor(private val characterStream: CharStream) {

    private val parseTree = lazy {
        characterStream
            .let { CSTLexer(it) }
            .let { CSTParser(CommonTokenStream(it)) }
    }

    companion object {
        fun fromString(input: String) = Parser(CharStreams.fromString(input))
    }

    fun program() = CSTProgramVisitor.visitProgram(parseTree.value.program())

    fun functionDefinition() = CSTFunctionDefinitionVisitor.visitFunctionDefinition(parseTree.value.functionDefinition())

    fun interfaceDefinition() = CSTInterfaceDefinitionVisitor.visitInterfaceDefinition(parseTree.value.interfaceDefinition())

}