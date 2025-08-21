package com.uabutler.cst.visitor.expression.util

import com.uabutler.cst.node.expression.util.CSTAccessor
import com.uabutler.cst.node.expression.util.CSTMemberAccessor
import com.uabutler.cst.node.expression.util.CSTVectorItemAccessor
import com.uabutler.cst.node.expression.util.CSTVectorSliceAccessor
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor.visitExpression
import com.uabutler.parsers.generated.CSTParser

object CSTAccessorVisitor: CSTVisitor() {

    fun visitAccessor(ctx: CSTParser.AccessorContext): CSTAccessor {
        return when (ctx) {
            is CSTParser.VectorItemAccessorContext -> visitVectorItemAccessor(ctx)
            is CSTParser.VectorSliceAccessorContext -> visitVectorSliceAccessor(ctx)
            is CSTParser.MemberAccessorContext -> visitMemberAccessor(ctx)
            else -> throw IllegalArgumentException("Unexpected accessor context $ctx")
        }
    }

    override fun visitVectorItemAccessor(ctx: CSTParser.VectorItemAccessorContext): CSTVectorItemAccessor {
        return CSTVectorItemAccessor(
            index = visitExpression(ctx.expression())
        )
    }

    override fun visitVectorSliceAccessor(ctx: CSTParser.VectorSliceAccessorContext): CSTVectorSliceAccessor {
        return CSTVectorSliceAccessor(
            start = visitExpression(ctx.startIndex!!),
            end = visitExpression(ctx.endIndex!!),
        )
    }

    override fun visitMemberAccessor(ctx: CSTParser.MemberAccessorContext): CSTMemberAccessor {
        return CSTMemberAccessor(
            portIdentifier = visitId(ctx.portIdentifier)
        )
    }

}