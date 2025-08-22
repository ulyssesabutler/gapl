package com.uabutler.cst.visitor.util

import com.uabutler.cst.node.util.CSTAtom
import com.uabutler.cst.visitor.CSTVisitor
import com.uabutler.parsers.generated.CSTParser

object CSTAtomVisitor: CSTVisitor() {

    override fun visitAtom(ctx: CSTParser.AtomContext): CSTAtom {
        return CSTAtom(
            identifier = visitId(ctx.identifier),
            interfaceValues = ctx.genericInterfaceValues()?.let { CSTGenericValueVisitor.visitGenericInterfaceValues(it).interfaceValues } ?: emptyList(),
            parameterValues = ctx.genericParameterValues()?.let { CSTGenericValueVisitor.visitGenericParameterValues(it).parameterValues } ?: emptyList(),
        ).also {
            if (it.interfaceValues.isNotEmpty() || it.parameterValues.isNotEmpty()) {
                val string = it.toString()
                if (string.contains("identifier=count,")) println("ATOM: $it")
            }
        }
    }

}