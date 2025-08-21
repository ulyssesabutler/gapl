package com.uabutler.cst.visitor.expression.util

import com.uabutler.cst.node.expression.util.CSTRecordTransformer
import com.uabutler.cst.node.expression.util.CSTRecordTransformerEntry
import com.uabutler.cst.node.expression.util.CSTTransformer
import com.uabutler.cst.node.expression.util.CSTVectorTransformer
import com.uabutler.cst.node.expression.util.CSTVectorTransformerEntry
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTCircuitExpressionVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTTransformerVisitor: CSTVisitor() {

    fun visitTransformer(ctx: CSTParser.TransformerContext): CSTTransformer {
        return when (ctx) {
            is CSTParser.RecordTransformerContext -> visitRecordTransformer(ctx)
            is CSTParser.VectorTransformerContext -> visitVectorTransformer(ctx)
            else -> throw IllegalArgumentException("Unexpected transformer context $ctx")
        }
    }

    override fun visitRecordTransformerEntry(ctx: CSTParser.RecordTransformerEntryContext): CSTRecordTransformerEntry {
        return CSTRecordTransformerEntry(
            portIdentifier = visitId(ctx.portIdentifier),
            transformation = CSTCircuitExpressionVisitor.visitCircuitExpression(ctx.circuitExpression())
        )
    }

    override fun visitVectorTransformerEntry(ctx: CSTParser.VectorTransformerEntryContext): CSTVectorTransformerEntry {
        return CSTVectorTransformerEntry(
            index = CSTExpressionVisitor.visitExpression(ctx.expression()),
            transformation = CSTCircuitExpressionVisitor.visitCircuitExpression(ctx.circuitExpression())
        )
    }

    override fun visitRecordTransformer(ctx: CSTParser.RecordTransformerContext): CSTRecordTransformer {
        return CSTRecordTransformer(
            entries = ctx.recordTransformerEntry().map { visitRecordTransformerEntry(it) }
        )
    }

    override fun visitVectorTransformer(ctx: CSTParser.VectorTransformerContext): CSTVectorTransformer {
        return CSTVectorTransformer(
            entries = ctx.vectorTransformerEntry().map { visitVectorTransformerEntry(it) }
        )
    }


}