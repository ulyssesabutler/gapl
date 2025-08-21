package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.FunctionGenericParameterTypeNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierGenericParameterTypeNode
import com.uabutler.cst.node.util.CSTFunctionGenericParameterDefinitionType
import com.uabutler.cst.node.util.CSTGenericParameterDefinition
import com.uabutler.cst.node.util.CSTNamedGenericParameterDefinitionType
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier

class GenericParameterDefinitionScope(
    parentScope: Scope,
    val genericParameterDefinition: CSTGenericParameterDefinition,
): Scope by parentScope {
    fun ast(): GenericParameterDefinitionNode {
        val identifier = genericParameterDefinition.declaredIdentifier.toIdentifier()

        val type = when (genericParameterDefinition.type) {
            is CSTNamedGenericParameterDefinitionType -> IdentifierGenericParameterTypeNode(genericParameterDefinition.type.name.toIdentifier())
            is CSTFunctionGenericParameterDefinitionType -> {
                FunctionGenericParameterTypeNode(
                    inputFunctionIO = emptyList(), // TODO: Needed for type validation
                    outputFunctionIO = emptyList(), // TODO: Needed for type validation
                )
            }
        }

        return GenericParameterDefinitionNode(identifier, type)
    }
}