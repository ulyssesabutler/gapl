package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.FunctionGenericParameterTypeNode
import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierGenericParameterTypeNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.SemanticTokenKind

class GenericParameterDefinitionScope(
    parentScope: Scope,
    val genericParameterDefinition: List<CSTParser.ParameterDefinitionContext>,
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

    private fun singleDefinition(definition: CSTParser.ParameterDefinitionContext): Definition {
        val span = SourceSpan.of(definition)
        val identifier = definition.declaredIdenfier!!.toIdentifierNode()
        semanticTokens.record(identifier.span, SemanticTokenKind.TYPE_PARAMETER)
        val type = definition.type!!
        val typeSpan = SourceSpan.of(type)

        return when (type) {
            is CSTParser.IntegerParameterDefinitionTypeContext -> GenericParameterDefinitionNode(
                span = span,
                identifier = identifier,
                type = IdentifierGenericParameterTypeNode(typeSpan, IdentifierNode(typeSpan, "integer")),
            ).toDefinition()

            is CSTParser.FunctionParameterDefinitionTypeContext -> GenericParameterDefinitionNode(
                span = span,
                identifier = identifier,
                type = FunctionGenericParameterTypeNode(
                    span = typeSpan,
                    inputFunctionIO = emptyList(), // TODO: Needed for type validation
                    outputFunctionIO = emptyList(), // TODO: Needed for type validation
                ),
            ).toDefinition()

            is CSTParser.InterfaceParameterDefinitionTypeContext -> GenericInterfaceDefinitionNode(span, identifier).toDefinition()

            else -> throw IllegalStateException("Unexpected parameter definition type $type")
        }
    }

}
