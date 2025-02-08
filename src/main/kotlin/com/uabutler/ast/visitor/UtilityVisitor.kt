package com.uabutler.ast.visitor

import com.uabutler.ast.node.*
import com.uabutler.parsers.generated.GAPLParser

object UtilityVisitor: GAPLVisitor() {

    override fun visitGenericInterfaceDefinitionList(ctx: GAPLParser.GenericInterfaceDefinitionListContext): GenericInterfaceDefinitionListNode {
        return GenericInterfaceDefinitionListNode(
            interfaces = ctx.Id().map { GenericInterfaceDefinitionNode(TokenVisitor.visitId(it)) }
        )
    }

    override fun visitGenericParameterDefinitionList(ctx: GAPLParser.GenericParameterDefinitionListContext): GenericParameterDefinitionListNode {
        return GenericParameterDefinitionListNode(
            parameters = ctx.genericParameterDefinition().map { visitGenericParameterDefinition(it) }
        )
    }

    override fun visitGenericParameterDefinition(ctx: GAPLParser.GenericParameterDefinitionContext): GenericParameterDefinitionNode {
        return GenericParameterDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.identifier),
            typeIdentifier = TokenVisitor.visitId(ctx.typeIdentifier),
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
            interfaces = ctx.interfaceExpression().map { GenericInterfaceValueNode(InterfaceVisitor.visitInterfaceExpression(it)) }
        )
    }

    override fun visitGenericParameterValueList(ctx: GAPLParser.GenericParameterValueListContext): GenericParameterValueListNode {
        return GenericParameterValueListNode(
            ctx.staticExpression().map { GenericParameterValueNode(StaticExpressionVisitor.visitStaticExpression(it)) }
        )
    }

}