package com.uabutler.ast.visitor

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.functions.*
import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.SignalInterfaceTypeNode
import com.uabutler.ast.node.functions.interfaces.StreamInterfaceTypeNode
import com.uabutler.parsers.generated.GAPLParser

object FunctionVisitor: GAPLVisitor() {

    override fun visitFunctionDefinition(ctx: GAPLParser.FunctionDefinitionContext): FunctionDefinitionNode {
        val input = when(val listNode = visitFunctionIOList(ctx.input!!)) {
            is EmptyFunctionIOListNode -> emptyList()
            is NonEmptyFunctionIOListNode -> listNode.functionIO
        }

        val output = when(val listNode = visitFunctionIOList(ctx.output!!)) {
            is EmptyFunctionIOListNode -> emptyList()
            is NonEmptyFunctionIOListNode -> listNode.functionIO
        }

        return FunctionDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            inputFunctionIO = input,
            outputFunctionIO = output,
            statements = ctx.circuitStatement().map { CircuitStatementVisitor.visitCircuitStatement(it) },
        )
    }

    override fun visitAbstractFunctionIO(ctx: GAPLParser.AbstractFunctionIOContext): AbstractFunctionIONode {
        return AbstractFunctionIONode(
            interfaceType = InterfaceVisitor.visitInterfaceType(ctx.interfaceType()),
            interfaceExpression = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression())
        )
    }

    fun visitAbstractFunctionIOList(ctx: GAPLParser.AbstractFunctionIOListContext): AbstractFunctionIOListNode {
        return when (ctx) {
            is GAPLParser.EmptyAbstractFunctionIOListContext -> visitEmptyAbstractFunctionIOList(ctx)
            is GAPLParser.NonEmptyAbstractFunctionIOListContext -> visitNonEmptyAbstractFunctionIOList(ctx)
            else -> throw Exception("Unknown function IO list type")
        }
    }

    override fun visitEmptyAbstractFunctionIOList(ctx: GAPLParser.EmptyAbstractFunctionIOListContext) = EmptyAbstractFunctionIOListNode

    override fun visitNonEmptyAbstractFunctionIOList(ctx: GAPLParser.NonEmptyAbstractFunctionIOListContext): NonEmptyAbstractFunctionIOListNode {
        return NonEmptyAbstractFunctionIOListNode(
            ctx.abstractFunctionIO().map { visitAbstractFunctionIO(it) }
        )
    }

    override fun visitFunctionIO(ctx: GAPLParser.FunctionIOContext): FunctionIONode {
        return FunctionIONode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            interfaceType = InterfaceVisitor.visitInterfaceType(ctx.interfaceType()),
            interfaceExpression = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression())
        )
    }

    fun visitFunctionIOList(ctx: GAPLParser.FunctionIOListContext): FunctionIOListNode {
        return when (ctx) {
            is GAPLParser.EmptyFunctionIOListContext -> visitEmptyFunctionIOList(ctx)
            is GAPLParser.NonEmptyFunctionIOListContext -> visitNonEmptyFunctionIOList(ctx)
            else -> throw Exception("Unknown function IO list type")
        }
    }

    override fun visitEmptyFunctionIOList(ctx: GAPLParser.EmptyFunctionIOListContext) = EmptyFunctionIOListNode

    override fun visitNonEmptyFunctionIOList(ctx: GAPLParser.NonEmptyFunctionIOListContext): NonEmptyFunctionIOListNode {
        return NonEmptyFunctionIOListNode(
            ctx.functionIO().map { visitFunctionIO(it) }
        )
    }

}