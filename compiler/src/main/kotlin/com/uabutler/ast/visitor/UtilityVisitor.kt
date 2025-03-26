package com.uabutler.ast.visitor

import com.uabutler.ast.node.*
import com.uabutler.ast.node.functions.EmptyAbstractFunctionIOListNode
import com.uabutler.ast.node.functions.EmptyFunctionIOListNode
import com.uabutler.ast.node.functions.NonEmptyAbstractFunctionIOListNode
import com.uabutler.ast.node.functions.NonEmptyFunctionIOListNode
import com.uabutler.ast.visitor.FunctionVisitor.visitAbstractFunctionIOList
import com.uabutler.ast.visitor.FunctionVisitor.visitFunctionIOList
import com.uabutler.ast.visitor.StaticExpressionVisitor.visitStaticExpression
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
            type = visitGenericParameter(ctx.genericParameterType())
        )
    }

    fun visitGenericParameter(ctx: GAPLParser.GenericParameterTypeContext): GenericParameterTypeNode {
        return when (ctx) {
            is GAPLParser.IdGenericParameterTypeContext -> visitIdGenericParameterType(ctx)
            is GAPLParser.FunctionGenericParameterTypeContext -> visitFunctionGenericParameterType(ctx)
            else -> throw Exception("Unexpected parameter type")
        }
    }

    override fun visitIdGenericParameterType(ctx: GAPLParser.IdGenericParameterTypeContext): IdentifierGenericParameterTypeNode {
        return IdentifierGenericParameterTypeNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
        )
    }

    override fun visitFunctionGenericParameterType(ctx: GAPLParser.FunctionGenericParameterTypeContext): FunctionGenericParameterTypeNode {
        val input = when(val listNode = visitAbstractFunctionIOList(ctx.input!!)) {
            is EmptyAbstractFunctionIOListNode -> emptyList()
            is NonEmptyAbstractFunctionIOListNode -> listNode.functionIO
        }

        val output = when(val listNode = visitAbstractFunctionIOList(ctx.output!!)) {
            is EmptyAbstractFunctionIOListNode -> emptyList()
            is NonEmptyAbstractFunctionIOListNode -> listNode.functionIO
        }

        return FunctionGenericParameterTypeNode(
            inputFunctionIO = input,
            outputFunctionIO = output,
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
            ctx.genericParameterValue().map { visitGenericParameterValue(it) }
        )
    }

    fun visitGenericParameterValue(ctx: GAPLParser.GenericParameterValueContext): GenericParameterValueNode {
        return when (ctx) {
            is GAPLParser.StaticExpressionGenericParameterValueContext -> visitStaticExpressionGenericParameterValue(ctx)
            is GAPLParser.FunctionInstantiationGenericParameterValueContext -> visitFunctionInstantiationGenericParameterValue(ctx)
            else -> throw Exception("Unexpected generic parameter value")
        }
    }

    override fun visitStaticExpressionGenericParameterValue(ctx: GAPLParser.StaticExpressionGenericParameterValueContext): StaticExpressionGenericParameterValueNode {
        return StaticExpressionGenericParameterValueNode(
            value = StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression())
        )
    }

    override fun visitFunctionInstantiationGenericParameterValue(ctx: GAPLParser.FunctionInstantiationGenericParameterValueContext): FunctionInstantiationGenericParameterValueNode {
        return FunctionInstantiationGenericParameterValueNode(
            instantiation = visitInstantiation(ctx.instantiation())
        )
    }

}