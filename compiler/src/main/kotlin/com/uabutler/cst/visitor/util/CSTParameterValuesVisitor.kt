package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.util.CSTParameterValues
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTParameterValuesVisitor: CSTVisitor() {

    override fun visitParameterValues(ctx: CSTParser.ParameterValuesContext): CSTParameterValues {
        return CSTParameterValues(
            parameterValues = ctx.expression().map { CSTExpressionVisitor.visitExpression(it) }
        )
    }

}