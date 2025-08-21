package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinition
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier

class GenericInterfaceDefinitionScope(
    parentScope: Scope,
    val genericInterfaceDefinition: CSTGenericInterfaceDefinition,
): Scope by parentScope {

    fun ast(): GenericInterfaceDefinitionNode {
        return GenericInterfaceDefinitionNode(
            identifier = genericInterfaceDefinition.declaredIdentifier.toIdentifier()
        )
    }

}