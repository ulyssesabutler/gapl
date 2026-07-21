package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.ErrorGenericParameterValueNode
import com.uabutler.ast.node.FunctionInstantiationGenericParameterValueNode
import com.uabutler.ast.node.FunctionReferenceGenericParameterValueNode
import com.uabutler.ast.node.GenericInterfaceValueNode
import com.uabutler.ast.node.GenericParameterValueNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.StaticExpressionGenericParameterValueNode
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.DeclaredSymbol
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope

class GenericParameterValueScope(
    override val parentScope: Scope,
    val genericParameterValues: List<CSTParser.ExpressionContext>,
): Scope {
    override fun resolveLocal(name: String): ResolvedSymbol? = null

    override fun symbols(): List<DeclaredSymbol> = emptyList()

    // Horrible, hacky bridge between the grammar's lack of caring about a parameter type and the AST very much caring.
    data class ParameterValues(
        val interfaces: List<GenericInterfaceValueNode>,
        val parameters: List<GenericParameterValueNode>,
    )

    fun ast(): ParameterValues {
        val parameterValues = genericParameterValues.map { singleParameter(it) }

        val interfaces = parameterValues.mapNotNull { it.singleInterface }
        val parameters = parameterValues.mapNotNull { it.singleParameter }

        return ParameterValues(interfaces, parameters)
    }

    private data class ParameterValue(
        val singleInterface: GenericInterfaceValueNode? = null,
        val singleParameter: GenericParameterValueNode? = null,
    )

    private fun GenericInterfaceValueNode.toParameterValue(): ParameterValue = ParameterValue(singleInterface = this)

    private fun GenericParameterValueNode.toParameterValue(): ParameterValue = ParameterValue(singleParameter = this)

    private fun singleParameter(value: CSTParser.ExpressionContext): ParameterValue {
        val span = SourceSpan.of(value)

        return when (value) {
            is CSTParser.TrueExpressionContext, is CSTParser.FalseExpressionContext,
            is CSTParser.LiteralExpressionContext, is CSTParser.ParenExpressionContext,
            is CSTParser.MultiplicaitonExpressionContext, is CSTParser.AdditionExpressionContext,
            is CSTParser.RelationalExpressionContext, is CSTParser.EqualityExpressionContext,
            is CSTParser.LogicalAndExpressionContext, is CSTParser.LogicalOrExpressionContext -> {
                StaticExpressionGenericParameterValueNode(span, StaticExpressionScope(parentScope, value).ast()).toParameterValue()
            }

            is CSTParser.AtomExpressionContext -> { // This can be a function, or a static expression.
                val atom = value.atom()!!
                val identifier = atom.identifier!!

                when (val declaration = resolve(identifier)) {
                    is ResolvedSymbol.Function -> {
                        val parameterValues = atom.parameterValues()?.expression() ?: emptyList()
                        val parameters = GenericParameterValueScope(this, parameterValues).ast()

                        FunctionInstantiationGenericParameterValueNode(
                            span = span,
                            instantiation = InstantiationNode(
                                span = span,
                                definitionIdentifier = identifier.toIdentifierNode(),
                                genericInterfaces = parameters.interfaces,
                                genericParameters = parameters.parameters,
                            )
                        ).toParameterValue()
                    }

                    is ResolvedSymbol.Parameter -> {
                        when (declaration.ctx.type) {
                            is CSTParser.IntegerParameterDefinitionTypeContext -> {
                                StaticExpressionGenericParameterValueNode(span, StaticExpressionScope(parentScope, value).ast()).toParameterValue()
                            }
                            is CSTParser.FunctionParameterDefinitionTypeContext -> {
                                FunctionReferenceGenericParameterValueNode(span, identifier.toIdentifierNode()).toParameterValue()
                            }
                            is CSTParser.InterfaceParameterDefinitionTypeContext -> GenericInterfaceValueNode(
                                span, InterfaceExpressionScope(this, value).ast()
                            ).toParameterValue()
                            else -> {
                                diagnostics.reportError(ResolverDiagnosticKind.UnexpectedGenericParameterType, span)
                                ErrorGenericParameterValueNode(span, "unexpected generic parameter type").toParameterValue()
                            }
                        }
                    }

                    is ResolvedSymbol.Interface -> GenericInterfaceValueNode(
                        span, InterfaceExpressionScope(this, value).ast()
                    ).toParameterValue()

                    null -> ErrorGenericParameterValueNode(span, "unresolved symbol '${identifier.text}'").toParameterValue()

                    else -> {
                        diagnostics.reportError(ResolverDiagnosticKind.CannotUseAsGenericParameterValue(identifier.text!!), span)
                        ErrorGenericParameterValueNode(span, "invalid generic parameter value").toParameterValue()
                    }
                }
            }

            is CSTParser.AccessorExpressionContext, is CSTParser.WireExpressionContext -> GenericInterfaceValueNode(
                span, InterfaceExpressionScope(this, value).ast()
            ).toParameterValue()

            else -> {
                diagnostics.reportError(ResolverDiagnosticKind.InvalidGenericParameterValue, span)
                ErrorGenericParameterValueNode(span, "invalid generic parameter value").toParameterValue()
            }
        }
    }
}
