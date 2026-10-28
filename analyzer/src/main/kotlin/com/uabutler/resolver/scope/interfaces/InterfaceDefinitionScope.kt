package com.uabutler.resolver.scope.interfaces

import com.uabutler.ast.node.functions.circuits.MemberAccessOperationNode
import com.uabutler.ast.node.functions.circuits.MultipleAccessOperationNode
import com.uabutler.ast.node.functions.circuits.SingleAccessOperationNode
import com.uabutler.ast.node.functions.circuits.SingleArrayAccessOperationNode
import com.uabutler.ast.node.interfaces.AliasInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfacePortNode
import com.uabutler.ast.node.interfaces.ViewFieldNode
import com.uabutler.ast.node.interfaces.ViewInterfaceDefinitionNode
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.DeclaredSymbol
import com.uabutler.resolver.scope.ProgramScope
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.SemanticTokenKind
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope
import com.uabutler.resolver.scope.util.GenericParameterDefinitionScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class InterfaceDefinitionScope(
    override val parentScope: ProgramScope,
    val interfaceDefinition: CSTParser.InterfaceDefinitionContext,
): Scope {

    private val parameterDefinitions =
        interfaceDefinition.aliasInterfaceDefinition()?.parameterDefinitionList()?.parameterDefinition()
            ?: interfaceDefinition.recordInterfaceDefinition()?.parameterDefinitionList()?.parameterDefinition()
            ?: interfaceDefinition.viewInterfaceDefinition()?.parameterDefinitionList()?.parameterDefinition()
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

        interfaceDefinition.viewInterfaceDefinition()?.let { view ->
            val identifier = view.declaredIdentifer!!.toIdentifierNode()
            semanticTokens.record(identifier.span, SemanticTokenKind.INTERFACE)
            val parameters = GenericParameterDefinitionScope(this, parameterDefinitions).ast()
            val base = InterfaceExpressionScope(this, view.base!!).ast()
            val fields = view.viewFieldDefinition().map { field -> viewField(field) }

            return ViewInterfaceDefinitionNode(
                span = span,
                identifier = identifier,
                genericInterfaces = parameters.interfaceDefinitions,
                genericParameters = parameters.parameterDefinitions,
                base = base,
                fields = fields,
            )
        }

        throw IllegalStateException("Interface definition is neither an alias, a record, nor a view")
    }

    // A field's path is split the same way a circuit reference's accessors are: member and index
    // accesses in order, then at most one slice, which must come last.
    private fun viewField(field: CSTParser.ViewFieldDefinitionContext): ViewFieldNode {
        val singleAccesses = mutableListOf<SingleAccessOperationNode>()
        var multipleAccess: MultipleAccessOperationNode? = null

        field.accessor().forEach { accessor ->
            if (multipleAccess != null) {
                diagnostics.reportError(ResolverDiagnosticKind.UnsupportedSliceAccess, SourceSpan.of(accessor))
            }
            when (accessor) {
                is CSTParser.MemberAccessorContext -> singleAccesses += MemberAccessOperationNode(
                    SourceSpan.of(accessor),
                    accessor.portIdentifier!!.toIdentifierNode(),
                )
                is CSTParser.VectorItemAccessorContext -> singleAccesses += SingleArrayAccessOperationNode(
                    SourceSpan.of(accessor),
                    StaticExpressionScope(this, accessor.expression()!!).ast(),
                )
                is CSTParser.VectorSliceAccessorContext -> multipleAccess = MultipleAccessOperationNode(
                    SourceSpan.of(accessor),
                    StaticExpressionScope(this, accessor.startIndex!!).ast(),
                    StaticExpressionScope(this, accessor.endIndex!!).ast(),
                )
                else -> throw IllegalStateException("Unexpected accessor context $accessor")
            }
        }

        return ViewFieldNode(
            span = SourceSpan.of(field),
            identifier = field.declaredIdentifier!!.toIdentifierNode(),
            singleAccesses = singleAccesses,
            multipleAccess = multipleAccess,
        )
    }
}
