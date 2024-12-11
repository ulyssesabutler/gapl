package com.uabutler.visitor

import com.uabutler.ast.*
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
        val current = GenericParameterDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.identifier),
            typeIdentifier = TokenVisitor.visitId(ctx.typeIdentifier),
        )
        current.identifier.parent = current
        current.typeIdentifier.parent = current
        return current
    }

    override fun visitInstantiation(ctx: GAPLParser.InstantiationContext): InstantiationNode {
        val current = InstantiationNode(
            definitionIdentifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = ctx.genericInterfaceValueList()?.let { visitGenericInterfaceValueList(it).interfaces } ?: emptyList(),
            genericParameters = visitGenericParameterValueList(ctx.genericParameterValueList()).parameters,
        )
        current.definitionIdentifier.parent = current
        current.genericInterfaces.forEach { it.parent = current }
        current.genericParameters.forEach { it.parent = current }
        return current
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