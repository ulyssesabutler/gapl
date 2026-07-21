package com.uabutler.resolver.scope.functions

import com.uabutler.ast.node.functions.FunctionIONode
import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class FunctionIOScope(
    parentScope: Scope,
    val functionIO: CSTParser.FunctionIOContext,
): Scope by parentScope {

    fun ast(): FunctionIONode {
        val span = SourceSpan.of(functionIO)
        val identifier = functionIO.declaredIdentifier!!.toIdentifierNode()
        val interfaceType = DefaultInterfaceTypeNode(span)
        val interfaceExpression = InterfaceExpressionScope(this, functionIO.interfaceType!!).ast()

        return FunctionIONode(span, identifier, interfaceType, interfaceExpression)
    }

}
