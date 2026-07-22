package com.uabutler.resolver.scope.interfaces

import com.uabutler.ast.node.interfaces.AliasInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfacePortNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.DeclaredSymbol
import com.uabutler.resolver.scope.ProgramScope
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.SemanticTokenKind
import com.uabutler.resolver.scope.util.GenericParameterDefinitionScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class InterfaceDefinitionScope(
    override val parentScope: ProgramScope,
    val interfaceDefinition: CSTParser.InterfaceDefinitionContext,
): Scope {

    private val parameterDefinitions =
        interfaceDefinition.aliasInterfaceDefinition()?.parameterDefinitionList()?.parameterDefinition()
            ?: interfaceDefinition.recordInterfaceDefinition()?.parameterDefinitionList()?.parameterDefinition()
            ?: emptyList()

    private val genericParameters = parameterDefinitions.associateBy { it.declaredIdenfier!!.text!! }

    override fun resolveLocal(name: String): ResolvedSymbol? {
        return genericParameters[name]?.let { ResolvedSymbol.Parameter(it) }
    }

    override fun symbols(): List<DeclaredSymbol> {
        return parameterDefinitions.map { DeclaredSymbol(it.declaredIdenfier!!.text!!, it.declaredIdenfier!!) }
    }

    fun ast(): InterfaceDefinitionNode {
        val span = SourceSpan.of(interfaceDefinition)

        interfaceDefinition.aliasInterfaceDefinition()?.let { alias ->
            val identifier = alias.declaredIdentifer!!.toIdentifierNode()
            semanticTokens.record(identifier.span, SemanticTokenKind.INTERFACE)
            val parameters = GenericParameterDefinitionScope(this, parameterDefinitions).ast()
            val aliasedInterface = InterfaceExpressionScope(this, alias.expression()!!).ast()

            return AliasInterfaceDefinitionNode(
                span = span,
                identifier = identifier,
                genericInterfaces = parameters.interfaceDefinitions,
                genericParameters = parameters.parameterDefinitions,
                aliasedInterface = aliasedInterface,
            )
        }

        interfaceDefinition.recordInterfaceDefinition()?.let { record ->
            val identifier = record.declaredIdentifer!!.toIdentifierNode()
            semanticTokens.record(identifier.span, SemanticTokenKind.INTERFACE)
            val parameters = GenericParameterDefinitionScope(this, parameterDefinitions).ast()
            val ports = record.portDefinition().map { port ->
                RecordInterfacePortNode(
                    span = SourceSpan.of(port),
                    identifier = port.declaredIdentifier!!.toIdentifierNode(),
                    type = InterfaceExpressionScope(this, port.interfaceType!!).ast(),
                )
            }

            return RecordInterfaceDefinitionNode(
                span = span,
                identifier = identifier,
                genericInterfaces = parameters.interfaceDefinitions,
                genericParameters = parameters.parameterDefinitions,
                inherits = emptyList(), // TODO: We don't really support this yet
                ports = ports,
            )
        }

        throw IllegalStateException("Interface definition is neither an alias nor a record")
    }
}
