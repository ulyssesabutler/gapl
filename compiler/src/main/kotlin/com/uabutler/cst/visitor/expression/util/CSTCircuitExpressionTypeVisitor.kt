package com.uabutler.cst.visitor.expression.util

import com.uabutler.cst.node.expression.util.CSTCircuitExpressionType
import com.uabutler.cst.node.expression.util.CSTInTransformerType
import com.uabutler.cst.node.expression.util.CSTInoutTransformerType
import com.uabutler.cst.node.expression.util.CSTBasicCircuitExpressionType
import com.uabutler.cst.node.expression.util.CSTOutTransformerType
import com.uabutler.cst.node.expression.util.CSTTransformerCircuitExpressionType
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTCircuitExpressionTypeVisitor: CSTVisitor() {

    fun visitCircuitExpressionType(ctx: CSTParser.CircuitExpressionTypeContext): CSTCircuitExpressionType {
        return when (ctx) {
            is CSTParser.BasicCircuitExpressionTypeContext -> visitBasicCircuitExpressionType(ctx)
            is CSTParser.TransformerCircuitExpressionTypeContext -> visitTransformerCircuitExpressionType(ctx)
            else -> throw IllegalArgumentException("Unexpected circuit expression type context $ctx")
        }
    }

    override fun visitBasicCircuitExpressionType(ctx: CSTParser.BasicCircuitExpressionTypeContext): CSTBasicCircuitExpressionType {
        return CSTBasicCircuitExpressionType(
            nodeType = CSTExpressionVisitor.visitExpression(ctx.expression())
        )
    }

    override fun visitTransformerCircuitExpressionType(ctx: CSTParser.TransformerCircuitExpressionTypeContext): CSTTransformerCircuitExpressionType {
        val type = when (val keyword = keywordFrom(ctx.transformerType)) {
            Companion.Keyword.IN -> CSTInTransformerType
            Companion.Keyword.OUT -> CSTOutTransformerType
            Companion.Keyword.INOUT -> CSTInoutTransformerType
            else -> throw IllegalArgumentException("Unexpected transformer type keyword $keyword")
        }

        return CSTTransformerCircuitExpressionType(
            transformer = CSTTransformerVisitor.visitTransformer(ctx.transformer()),
            transformerType = type,
            nodeType = CSTExpressionVisitor.visitExpression(ctx.expression()),
        )
    }

}