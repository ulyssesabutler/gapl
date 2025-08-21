package com.uabutler.resolver.scope.functions

import com.uabutler.ast.node.functions.FunctionIONode
import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.cst.node.functions.CSTFunctionIO
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope

class FunctionIOScope(
    parentScope: Scope,
    val functionIO: CSTFunctionIO,
): Scope by parentScope {

    fun ast(): FunctionIONode {
        val identifier = functionIO.declaredIdentifier.toIdentifier()
        val interfaceType = DefaultInterfaceTypeNode()
        val interfaceExpression = InterfaceExpressionScope(this, functionIO.interfaceType).ast()

        return FunctionIONode(identifier, interfaceType, interfaceExpression)
    }

}