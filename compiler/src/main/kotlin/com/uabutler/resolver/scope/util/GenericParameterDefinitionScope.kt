package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.FunctionGenericParameterTypeNode
import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierGenericParameterTypeNode
import com.uabutler.cst.node.util.CSTFunctionParameterDefinitionType
import com.uabutler.cst.node.util.CSTIntegerParameterDefinitionType
import com.uabutler.cst.node.util.CSTInterfaceParameterDefinitionType
import com.uabutler.cst.node.util.CSTParameterDefinition
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier

class GenericParameterDefinitionScope(
    parentScope: Scope,
    val genericParameterDefinition: List<CSTParameterDefinition>,
): Scope by parentScope {

    data class Definitions(
        val parameterDefinitions: List<GenericParameterDefinitionNode>,
        val interfaceDefinitions: List<GenericInterfaceDefinitionNode>,
    )

    private class Definition(
        val parameterDefinitions: GenericParameterDefinitionNode? = null,
        val interfaceDefinitions: GenericInterfaceDefinitionNode? = null,
    )

    private fun GenericParameterDefinitionNode.toDefinition(): Definition {
        return Definition(parameterDefinitions = this)
    }

    private fun GenericInterfaceDefinitionNode.toDefinition(): Definition {
        return Definition(interfaceDefinitions = this)
    }

    fun ast(): Definitions {
        val definitions = genericParameterDefinition.map { singleDefinition(it) }

        val parameterDefinitions = definitions.mapNotNull { it.parameterDefinitions }
        val interfaceDefinitions = definitions.mapNotNull { it.interfaceDefinitions }

        return Definitions(parameterDefinitions, interfaceDefinitions)
    }

    private fun singleDefinition(definition: CSTParameterDefinition): Definition {
        val identifier = definition.declaredIdentifier.toIdentifier()

        return when (definition.type) {
            is CSTIntegerParameterDefinitionType -> GenericParameterDefinitionNode(
                identifier = identifier,
                type = IdentifierGenericParameterTypeNode("integer".toIdentifier()),
            ).toDefinition()

            is CSTFunctionParameterDefinitionType -> GenericParameterDefinitionNode(
                identifier = identifier,
                type = FunctionGenericParameterTypeNode(
                    inputFunctionIO = emptyList(), // TODO: Needed for type validation
                    outputFunctionIO = emptyList(), // TODO: Needed for type validation
                ),
            ).toDefinition()

            is CSTInterfaceParameterDefinitionType -> GenericInterfaceDefinitionNode(identifier).toDefinition()
        }
    }

}