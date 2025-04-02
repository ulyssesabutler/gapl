package com.uabutler

import com.uabutler.ast.visitor.FunctionVisitor
import com.uabutler.ast.visitor.InterfaceVisitor
import com.uabutler.parsers.generated.GAPLLexer
import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.ast.visitor.ProgramVisitor
import com.uabutler.ast.visitor.StaticExpressionVisitor
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
    }

    fun program() = ProgramVisitor.visitProgram(parseTree.value.program())

    fun functionDefinition() = FunctionVisitor.visitFunctionDefinition(parseTree.value.functionDefinition())

    fun staticExpression() = StaticExpressionVisitor.visitStaticExpression(parseTree.value.staticExpression())

    fun interfaceDefinition() = InterfaceVisitor.visitInterfaceDefinition(parseTree.value.interfaceDefinition())

}