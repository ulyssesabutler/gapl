package com.uabutler.v2.ast.visitor

import com.uabutler.v2.ast.node.ProgramNode
import com.uabutler.parsers.generated.GAPLParser

object ProgramVisitor: GAPLVisitor() {

    override fun visitProgram(ctx: GAPLParser.ProgramContext): ProgramNode {
        return ProgramNode(
            interfaces = ctx.interfaceDefinition().map { InterfaceVisitor.visitInterfaceDefinition(it) },
            functions = ctx.functionDefinition().map { FunctionVisitor.visitFunctionDefinition(it) },
        )
    }

}