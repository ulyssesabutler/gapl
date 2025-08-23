package com.uabutler.cst.visitor.interfaces

import com.uabutler.cst.node.interfaces.CSTAliasInterfaceDefinition
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition
import com.uabutler.cst.node.interfaces.CSTPortDefinition
import com.uabutler.cst.node.interfaces.CSTRecordInterfaceDefinition
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.cst.visitor.util.CSTParameterDefinitionListVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTInterfaceDefinitionVisitor: CSTVisitor() {

    override fun visitInterfaceDefinition(ctx: CSTParser.InterfaceDefinitionContext): CSTInterfaceDefinition {
        ctx.aliasInterfaceDefinition()?.let { return visitAliasInterfaceDefinition(it) }
        ctx.recordInterfaceDefinition()?.let { return visitRecordInterfaceDefinition(it) }
        throw Exception("Invalid interface definition")
    }

    override fun visitPortDefinition(ctx: CSTParser.PortDefinitionContext): CSTPortDefinition {
        return CSTPortDefinition(
            declaredIdentifier = visitId(ctx.declaredIdentifier),
            interfaceType = CSTExpressionVisitor.visitExpression(ctx.expression()),
        )
    }

    override fun visitAliasInterfaceDefinition(ctx: CSTParser.AliasInterfaceDefinitionContext): CSTAliasInterfaceDefinition {
        return CSTAliasInterfaceDefinition(
            declaredIdentifier = visitId(ctx.declaredIdentifer),
            parameterDefinitions = ctx.parameterDefinitionList()?.let { CSTParameterDefinitionListVisitor.visitParameterDefinitionList(it).definitions } ?: emptyList(),
            aliasedInterface = CSTExpressionVisitor.visitExpression(ctx.expression()),
        )
    }

    override fun visitRecordInterfaceDefinition(ctx: CSTParser.RecordInterfaceDefinitionContext): CSTRecordInterfaceDefinition {
        return CSTRecordInterfaceDefinition(
            declaredIdentifier = visitId(ctx.declaredIdentifer),
            parameterDefinitions = ctx.parameterDefinitionList()?.let { CSTParameterDefinitionListVisitor.visitParameterDefinitionList(it).definitions } ?: emptyList(),
            ports = ctx.portDefinition().map { visitPortDefinition(it) },
        )
    }

}