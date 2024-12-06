package com.uabutler.visitor

import com.uabutler.ast.ProgramNode
import com.uabutler.parsers.generated.GAPLParser

object ProgramVisitor: GAPLVisitor() {

    override fun visitProgram(ctx: GAPLParser.ProgramContext): ProgramNode {
        return ProgramNode(
            ctx.interfaceDefinition().map { InterfaceVisitor.visitInterfaceDefinition(it) }
        )
    }

}