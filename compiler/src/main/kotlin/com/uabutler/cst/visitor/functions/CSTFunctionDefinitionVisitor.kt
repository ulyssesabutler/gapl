package com.uabutler.cst.visitor.functions

import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.cst.node.functions.CSTFunctionIO
import com.uabutler.cst.node.functions.CSTFunctionIOList
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.cst.visitor.util.CSTParameterDefinitionListVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTFunctionDefinitionVisitor: CSTVisitor() {

    override fun visitFunctionIO(ctx: CSTParser.FunctionIOContext): CSTFunctionIO {
        return CSTFunctionIO(
            declaredIdentifier = visitId(ctx.declaredIdentifier),
            interfaceType = CSTExpressionVisitor.visitExpression(ctx.expression()),
        )
    }

    override fun visitFunctionIOList(ctx: CSTParser.FunctionIOListContext): CSTFunctionIOList {
        return CSTFunctionIOList(
            ioList = ctx.functionIO().map { visitFunctionIO(it) }
        )
    }

    override fun visitFunctionDefinition(ctx: CSTParser.FunctionDefinitionContext): CSTFunctionDefinition {
        return CSTFunctionDefinition(
            declaredIdentifier = visitId(ctx.declaredIdentifier),
            parameterDefinitions = ctx.parameterDefinitionList()?.let { CSTParameterDefinitionListVisitor.visitParameterDefinitionList(it).definitions } ?: emptyList(),
            inputs = visitFunctionIOList(ctx.input!!).ioList,
            outputs = visitFunctionIOList(ctx.output!!).ioList,
            statements = ctx.circuitStatement().map { CSTCircuitStatementVisitor.visitCircuitStatement(it) },
        )
    }

}