package com.uabutler.v1.visitor

import com.uabutler.v1.ast.ProgramNode
import com.uabutler.parsers.generated.GAPLParser

object ProgramVisitor: GAPLVisitor() {

    override fun visitProgram(ctx: GAPLParser.ProgramContext): ProgramNode {
        val current = ProgramNode(
            interfaces = ctx.interfaceDefinition().map { InterfaceVisitor.visitInterfaceDefinition(it) },
            functions = ctx.functionDefinition().map { FunctionVisitor.visitFunctionDefinition(it) },
        )
        current.interfaces.forEach { it.parent = current }
        current.functions.forEach { it.parent = current }
        return current
    }

}