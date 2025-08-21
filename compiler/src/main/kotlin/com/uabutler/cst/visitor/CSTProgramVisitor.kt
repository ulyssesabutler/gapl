package com.uabutler.cst.visitor

import com.uabutler.cst.node.CSTProgram
import com.uabutler.cst.visitor.functions.CSTFunctionDefinitionVisitor
import com.uabutler.cst.visitor.interfaces.CSTInterfaceDefinitionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTProgramVisitor: CSTVisitor() {

    override fun visitProgram(ctx: CSTParser.ProgramContext): CSTProgram {
        return CSTProgram(
            interfaceDefinitions = ctx.interfaceDefinition().map { CSTInterfaceDefinitionVisitor.visitInterfaceDefinition(it) },
            functionDefinitions = ctx.functionDefinition().map { CSTFunctionDefinitionVisitor.visitFunctionDefinition(it) },
        )
    }

}