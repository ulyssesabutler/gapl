package com.uabutler.visitor

import com.uabutler.ast.functions.*
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

    override fun visitFunctionIO(ctx: GAPLParser.FunctionIOContext): FunctionIONode {
        return FunctionIONode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            ioType = visitFunctionIOType(ctx.functionIOType()),
            interfaceType = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression())
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

    override fun visitFunctionType(ctx: GAPLParser.FunctionTypeContext): FunctionTypeNode {
        return if (ctx.Sequential() != null) SequentialFunctionTypeNode
        else if (ctx.Combinational() != null) CombinationalFunctionTypeNode
        else DefaultFunctionTypeNode
    }

    override fun visitFunctionIOType(ctx: GAPLParser.FunctionIOTypeContext): FunctionIOTypeNode {
        return if (ctx.Sequential() != null) SequentialFunctionIOTypeNode
        else if (ctx.Combinational() != null) CombinationalFunctionIOTypeNode
        else DefaultFunctionIOTypeNode
    }

}