package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.GenericInterfaceValueNode
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope

class GenericInterfaceValueScope(
    parentScope: Scope,
    val genericInterfaceValue: CSTExpression,
): Scope by parentScope {

    fun ast(): GenericInterfaceValueNode {
        return GenericInterfaceValueNode(
            value = InterfaceExpressionScope(this, genericInterfaceValue).ast()
        )
    }

}