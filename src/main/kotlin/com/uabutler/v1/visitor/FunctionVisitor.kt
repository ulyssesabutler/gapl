package com.uabutler.v1.visitor

import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.v1.ast.functions.*

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

        val current = FunctionDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = UtilityVisitor.visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = UtilityVisitor.visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            inputFunctionIO = input,
            outputFunctionIO = output,
            statements = ctx.circuitStatement().map { CircuitStatementVisitor.visitCircuitStatement(it) },
        )

        current.identifier.parent = current
        current.genericInterfaces.forEach { it.parent = current }
        current.genericParameters.forEach { it.parent = current }
        current.inputFunctionIO.forEach { it.parent = current }
        current.outputFunctionIO.forEach { it.parent = current }
        current.statements.forEach { it.parent = current }

        return current
    }

    override fun visitFunctionIO(ctx: GAPLParser.FunctionIOContext): FunctionIONode {
        val current = FunctionIONode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            ioType = visitFunctionIOType(ctx.functionIOType()),
            interfaceType = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression())
        )

        current.identifier.parent = current
        current.ioType.parent = current
        current.interfaceType.parent = current

        return current
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
        return if (ctx.Sequential() != null) SequentialFunctionTypeNode()
        else if (ctx.Combinational() != null) CombinationalFunctionTypeNode()
        else DefaultFunctionTypeNode()
    }

    override fun visitFunctionIOType(ctx: GAPLParser.FunctionIOTypeContext): FunctionIOTypeNode {
        return if (ctx.Sequential() != null) SequentialFunctionIOTypeNode()
        else if (ctx.Combinational() != null) CombinationalFunctionIOTypeNode()
        else DefaultFunctionIOTypeNode()
    }

}