package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.util.CSTGenericInterfaceValues
import com.uabutler.cst.node.util.CSTGenericParameterValues
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.cst.visitor.expression.CSTExpressionVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTGenericValueVisitor: CSTVisitor() {

    override fun visitGenericInterfaceValues(ctx: CSTParser.GenericInterfaceValuesContext): CSTGenericInterfaceValues {
        return CSTGenericInterfaceValues(
            interfaceValues = ctx.expression().map { CSTExpressionVisitor.visitExpression(it) }
        )
    }

    override fun visitGenericParameterValues(ctx: CSTParser.GenericParameterValuesContext): CSTGenericParameterValues {
        return CSTGenericParameterValues(
            parameterValues = ctx.expression().map { CSTExpressionVisitor.visitExpression(it) }
        )
    }

}