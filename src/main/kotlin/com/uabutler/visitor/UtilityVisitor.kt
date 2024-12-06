package com.uabutler.visitor

import com.uabutler.ast.*
import com.uabutler.parsers.generated.GAPLParser

object UtilityVisitor: GAPLVisitor() {

    override fun visitGenericInterfaceDefinitionList(ctx: GAPLParser.GenericInterfaceDefinitionListContext): GenericInterfaceDefinitionListNode {
        return GenericInterfaceDefinitionListNode(
            ctx.Id().map { GenericInterfaceDefinitionNode(TokenVisitor.visitId(it)) }
        )
    }

    override fun visitGenericParameterDefinitionList(ctx: GAPLParser.GenericParameterDefinitionListContext): GenericParameterDefinitionListNode {
        return GenericParameterDefinitionListNode(
            ctx.genericParameterDefinition().map { visitGenericParameterDefinition(it) }
        )
    }

    override fun visitGenericParameterDefinition(ctx: GAPLParser.GenericParameterDefinitionContext): GenericParameterDefinitionNode {
        return GenericParameterDefinitionNode(
            TokenVisitor.visitId(ctx.identifier),
            TokenVisitor.visitId(ctx.typeIdentifier),
        )
    }

    override fun visitInstantiation(ctx: GAPLParser.InstantiationContext): InstantiationNode {
        return InstantiationNode(
            definitionIdentifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = ctx.genericInterfaceValueList()?.let { visitGenericInterfaceValueList(it).interfaces } ?: emptyList(),
            genericParameters = visitGenericParameterValueList(ctx.genericParameterValueList()).parameters,
        )
    }

    override fun visitGenericInterfaceValueList(ctx: GAPLParser.GenericInterfaceValueListContext): GenericInterfaceValueListNode {
        return GenericInterfaceValueListNode(
            ctx.interfaceExpression().map { GenericInterfaceValueNode(InterfaceVisitor.visitInterfaceExpression(it)) }
        )
    }

    override fun visitGenericParameterValueList(ctx: GAPLParser.GenericParameterValueListContext): GenericParameterValueListNode {
        return GenericParameterValueListNode(
            ctx.staticExpression().map { GenericParameterValueNode(StaticExpressionVisitor.visitStaticExpression(it)) }
        )
    }

}