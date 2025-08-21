package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.util.CSTAtom
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTAtomVisitor: CSTVisitor() {

    override fun visitAtom(ctx: CSTParser.AtomContext): CSTAtom {
        return CSTAtom(
            identifier = visitId(ctx.identifier),
            interfaceValues = CSTGenericValueVisitor.visitGenericInterfaceValues(ctx.genericInterfaceValues()!!).interfaceValues,
            parameterValues = CSTGenericValueVisitor.visitGenericParameterValues(ctx.genericParameterValues()!!).parameterValues,
        )
    }

}