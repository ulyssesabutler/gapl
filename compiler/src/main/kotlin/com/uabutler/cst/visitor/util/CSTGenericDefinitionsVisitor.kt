package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.util.CSTFunctionGenericParameterDefinitionType
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinition
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinitions
import com.uabutler.cst.node.util.CSTGenericParameterDefinition
import com.uabutler.cst.node.util.CSTGenericParameterDefinitionType
import com.uabutler.cst.node.util.CSTGenericParameterDefinitionTypeInterfaceList
import com.uabutler.cst.node.util.CSTGenericParameterDefinitions
import com.uabutler.cst.node.util.CSTNamedGenericParameterDefinitionType
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTGenericDefinitionsVisitor: CSTVisitor() {

    override fun visitGenericParameterDefinitionTypeInterfaceList(ctx: CSTParser.GenericParameterDefinitionTypeInterfaceListContext): CSTGenericParameterDefinitionTypeInterfaceList {
        return CSTGenericParameterDefinitionTypeInterfaceList(
            interfaceTypes = ctx.expression().map { CSTExpressionVisitor.visitExpression(it) }
        )
    }

    fun visitGenericParameterDefinitionType(ctx: CSTParser.GenericParameterDefinitionTypeContext): CSTGenericParameterDefinitionType {
        return when (ctx) {
            is CSTParser.NamedGenericParameterDefinitionTypeContext -> visitNamedGenericParameterDefinitionType(ctx)
            is CSTParser.FunctionGenericParameterDefinitionTypeContext -> visitFunctionGenericParameterDefinitionType(ctx)
            else -> throw IllegalArgumentException("Unexpected generic parameter definition type context $ctx")
        }
    }

    override fun visitNamedGenericParameterDefinitionType(ctx: CSTParser.NamedGenericParameterDefinitionTypeContext): CSTNamedGenericParameterDefinitionType {
        return CSTNamedGenericParameterDefinitionType(
            name = visitId(ctx.typeName)
        )
    }

    override fun visitFunctionGenericParameterDefinitionType(ctx: CSTParser.FunctionGenericParameterDefinitionTypeContext): CSTFunctionGenericParameterDefinitionType {
        return CSTFunctionGenericParameterDefinitionType(
            inputs = visitGenericParameterDefinitionTypeInterfaceList(ctx.inputs!!).interfaceTypes,
            outputs = visitGenericParameterDefinitionTypeInterfaceList(ctx.outputs!!).interfaceTypes,
        )
    }

    override fun visitGenericParameterDefinition(ctx: CSTParser.GenericParameterDefinitionContext): CSTGenericParameterDefinition {
        return CSTGenericParameterDefinition(
            declaredIdentifier = visitId(ctx.declaredIdenfier),
            type = visitGenericParameterDefinitionType(ctx.genericParameterDefinitionType())
        )
    }

    override fun visitGenericParameterDefinitions(ctx: CSTParser.GenericParameterDefinitionsContext): CSTGenericParameterDefinitions {
        return CSTGenericParameterDefinitions(
            definitions = ctx.genericParameterDefinition().map { visitGenericParameterDefinition(it) }
        )
    }

    override fun visitGenericInterfaceDefinition(ctx: CSTParser.GenericInterfaceDefinitionContext): CSTGenericInterfaceDefinition {
        return CSTGenericInterfaceDefinition(
            declaredIdentifier = visitId(ctx.declaredIdentifier)
        )
    }

    override fun visitGenericInterfaceDefinitions(ctx: CSTParser.GenericInterfaceDefinitionsContext): CSTGenericInterfaceDefinitions {
        return CSTGenericInterfaceDefinitions(
            definitions = ctx.genericInterfaceDefinition().map { visitGenericInterfaceDefinition(it) }
        )
    }

}