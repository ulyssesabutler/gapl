package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.CST
import com.uabutler.cst.node.util.CSTFunctionParameterDefinitionType
import com.uabutler.cst.node.util.CSTIntegerParameterDefinitionType
import com.uabutler.cst.node.util.CSTInterfaceParameterDefinitionType
import com.uabutler.cst.node.util.CSTParameterDefinition
import com.uabutler.cst.node.util.CSTParameterDefinitionList
import com.uabutler.cst.node.util.CSTParameterDefinitionType
import com.uabutler.cst.node.util.CSTParameterDefinitionTypeInterfaceList
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTParameterDefinitionListVisitor: CSTVisitor() {

    override fun visitParameterDefinitionList(ctx: CSTParser.ParameterDefinitionListContext): CSTParameterDefinitionList {
        return CSTParameterDefinitionList(
            definitions = ctx.parameterDefinition().map { visitParameterDefinition(it) }
        )
    }

    override fun visitParameterDefinition(ctx: CSTParser.ParameterDefinitionContext): CSTParameterDefinition {
        return CSTParameterDefinition(
            declaredIdentifier = visitId(ctx.declaredIdenfier),
            type = visitParameterDefinitionType(ctx.type!!),
        )
    }

    fun visitParameterDefinitionType(ctx: CSTParser.ParameterDefinitionTypeContext): CSTParameterDefinitionType {
        return when (ctx) {
            is CSTParser.IntegerParameterDefinitionTypeContext -> visitIntegerParameterDefinitionType(ctx)
            is CSTParser.InterfaceParameterDefinitionTypeContext -> visitInterfaceParameterDefinitionType(ctx)
            is CSTParser.FunctionParameterDefinitionTypeContext -> visitFunctionParameterDefinitionType(ctx)
            else -> throw Exception("Unknown parameter definition type")
        }
    }

    override fun visitIntegerParameterDefinitionType(ctx: CSTParser.IntegerParameterDefinitionTypeContext): CSTIntegerParameterDefinitionType {
        return CSTIntegerParameterDefinitionType
    }

    override fun visitInterfaceParameterDefinitionType(ctx: CSTParser.InterfaceParameterDefinitionTypeContext): CSTInterfaceParameterDefinitionType {
        return CSTInterfaceParameterDefinitionType
    }

    override fun visitParameterDefinitionTypeInterfaceList(ctx: CSTParser.ParameterDefinitionTypeInterfaceListContext): CSTParameterDefinitionTypeInterfaceList {
        return CSTParameterDefinitionTypeInterfaceList(
            interfaceTypes = ctx.expression().map { CSTExpressionVisitor.visitExpression(it) }
        )
    }

    override fun visitFunctionParameterDefinitionType(ctx: CSTParser.FunctionParameterDefinitionTypeContext): CSTFunctionParameterDefinitionType {
        return CSTFunctionParameterDefinitionType(
            // TODO: Support null for no IO
            inputs = visitParameterDefinitionTypeInterfaceList(ctx.inputs!!).interfaceTypes,
            outputs = visitParameterDefinitionTypeInterfaceList(ctx.outputs!!).interfaceTypes,
        )
    }

}