package com.uabutler.v1.visitor

import com.uabutler.v1.ast.VectorBoundsNode
import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.v1.ast.interfaces.*

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
        val current = AliasInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            aliasedInterface = visitInterfaceExpression(ctx.interfaceExpression()),
        )
        current.identifier.parent = current
        current.genericInterfaces.forEach { it.parent = current }
        current.genericParameters.forEach { it.parent = current }
        current.aliasedInterface.parent = current
        return current
    }

    override fun visitRecordInterfaceDefinition(ctx: GAPLParser.RecordInterfaceDefinitionContext): RecordInterfaceDefinitionNode {
        val current = RecordInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            inherits = ctx.inheritList()?.let { visitInheritList(it).inherits } ?: emptyList(),
            ports = visitPortDefinitionList(ctx.portDefinitionList()).ports,
        )
        current.identifier.parent = current
        current.genericParameters.forEach { it.parent = current }
        current.genericParameters.forEach { it.parent = current }
        current.inherits.forEach { it.parent = current }
        current.ports.forEach { it.parent = current }
        return current
    }

    override fun visitWireInterfaceExpression(ctx: GAPLParser.WireInterfaceExpressionContext): WireInterfaceExpressionNode {
        return WireInterfaceExpressionNode()
    }

    override fun visitDefinedInterfaceExpression(ctx: GAPLParser.DefinedInterfaceExpressionContext): DefinedInterfaceExpressionNode {
        val instantiation = UtilityVisitor.visitInstantiation(ctx.instantiation())
        val current = DefinedInterfaceExpressionNode(
            interfaceIdentifier = instantiation.definitionIdentifier,
            genericInterfaces = instantiation.genericInterfaces,
            genericParameters = instantiation.genericParameters,
        )
        current.interfaceIdentifier.parent = current
        current.genericInterfaces.forEach { it.parent = current }
        current.genericParameters.forEach { it.parent = current }
        return current
    }

    override fun visitVectorInterfaceExpression(ctx: GAPLParser.VectorInterfaceExpressionContext): VectorInterfaceExpressionNode {
        val current = VectorInterfaceExpressionNode(
            vectoredInterface = visitInterfaceExpression(ctx.interfaceExpression()),
            boundsSpecifier = VectorBoundsNode(StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression())),
        )
        current.vectoredInterface.parent = current
        current.boundsSpecifier.parent = current
        current.boundsSpecifier.boundSpecifier.parent = current.boundsSpecifier
        return current
    }

    override fun visitIdentifierInterfaceExpression(ctx: GAPLParser.IdentifierInterfaceExpressionContext): IdentifierInterfaceExpressionNode {
        val current = IdentifierInterfaceExpressionNode(TokenVisitor.visitId(ctx.Id()))
        current.interfaceIdentifier.parent = current
        return current
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
        val current = RecordInterfacePortNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            type = visitInterfaceExpression(ctx.interfaceExpression()),
        )
        current.identifier.parent = current
        current.type.parent = current
        return current
    }

}