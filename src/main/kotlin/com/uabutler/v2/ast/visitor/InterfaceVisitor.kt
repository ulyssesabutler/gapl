package com.uabutler.v2.ast.visitor

import com.uabutler.v2.ast.node.VectorBoundsNode
import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.v2.ast.node.interfaces.*

object InterfaceVisitor: GAPLVisitor() {

    override fun visitInterfaceDefinition(ctx: GAPLParser.InterfaceDefinitionContext): InterfaceDefinitionNode {
        ctx.aliasInterfaceDefinition()?.let { return visitAliasInterfaceDefinition(it) }
        ctx.recordInterfaceDefinition()?.let { return visitRecordInterfaceDefinition(it) }
        throw Exception("Invalid interface definition")
    }

    fun visitInterfaceExpression(ctx: GAPLParser.InterfaceExpressionContext): InterfaceExpressionNode {
        return when (ctx) {
            is GAPLParser.WireInterfaceExpressionContext -> visitWireInterfaceExpression(ctx)
            is GAPLParser.DefinedInterfaceExpressionContext -> visitDefinedInterfaceExpression(ctx)
            is GAPLParser.VectorInterfaceExpressionContext -> visitVectorInterfaceExpression(ctx)
            is GAPLParser.IdentifierInterfaceExpressionContext -> visitIdentifierInterfaceExpression(ctx)
            else -> throw Exception("Unknown interface")
        }
    }

    override fun visitAliasInterfaceDefinition(ctx: GAPLParser.AliasInterfaceDefinitionContext): AliasInterfaceDefinitionNode {
        return AliasInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            aliasedInterface = visitInterfaceExpression(ctx.interfaceExpression()),
        )
    }

    override fun visitRecordInterfaceDefinition(ctx: GAPLParser.RecordInterfaceDefinitionContext): RecordInterfaceDefinitionNode {
        return RecordInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            inherits = ctx.inheritList()?.let { visitInheritList(it).inherits } ?: emptyList(),
            ports = visitPortDefinitionList(ctx.portDefinitionList()).ports,
        )
    }

    override fun visitWireInterfaceExpression(ctx: GAPLParser.WireInterfaceExpressionContext): WireInterfaceExpressionNode {
        return WireInterfaceExpressionNode()
    }

    override fun visitDefinedInterfaceExpression(ctx: GAPLParser.DefinedInterfaceExpressionContext): DefinedInterfaceExpressionNode {
        val instantiation = UtilityVisitor.visitInstantiation(ctx.instantiation())
        return DefinedInterfaceExpressionNode(
            interfaceIdentifier = instantiation.definitionIdentifier,
            genericInterfaces = instantiation.genericInterfaces,
            genericParameters = instantiation.genericParameters,
        )
    }

    override fun visitVectorInterfaceExpression(ctx: GAPLParser.VectorInterfaceExpressionContext): VectorInterfaceExpressionNode {
        return VectorInterfaceExpressionNode(
            vectoredInterface = visitInterfaceExpression(ctx.interfaceExpression()),
            boundsSpecifier = VectorBoundsNode(StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression())),
        )
    }

    override fun visitIdentifierInterfaceExpression(ctx: GAPLParser.IdentifierInterfaceExpressionContext): IdentifierInterfaceExpressionNode {
        return IdentifierInterfaceExpressionNode(TokenVisitor.visitId(ctx.Id()))
    }

    override fun visitInheritList(ctx: GAPLParser.InheritListContext): RecordInterfaceInheritListNode {
        return RecordInterfaceInheritListNode(
            ctx.interfaceExpression()
                .onEach { assert(it is GAPLParser.DefinedInterfaceExpressionContext) } // TODO: Raise syntax error
                .filterIsInstance<GAPLParser.DefinedInterfaceExpressionContext>()
                .map { visitDefinedInterfaceExpression(it) }
        )
    }

    override fun visitPortDefinitionList(ctx: GAPLParser.PortDefinitionListContext): RecordInterfacePortListNode {
        return RecordInterfacePortListNode(
            ctx.portDefinition().map { visitPortDefinition(it) }
        )
    }

    override fun visitPortDefinition(ctx: GAPLParser.PortDefinitionContext): RecordInterfacePortNode {
        return RecordInterfacePortNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            type = visitInterfaceExpression(ctx.interfaceExpression()),
        )
    }

}