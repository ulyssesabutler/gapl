package com.uabutler.resolver.scope.interfaces

import com.uabutler.ast.node.VectorBoundsNode
import com.uabutler.ast.node.interfaces.DefinedInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.ErrorInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.IdentifierInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.ast.node.interfaces.VectorInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.WireInterfaceExpressionNode
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope
import com.uabutler.resolver.scope.util.GenericParameterValueScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class InterfaceExpressionScope(
    override val parentScope: Scope,
    val interfaceExpression: CSTParser.ExpressionContext,
): Scope by parentScope {

    fun ast(): InterfaceExpressionNode {
        val span = SourceSpan.of(interfaceExpression)

        return when (interfaceExpression) {
            is CSTParser.WireExpressionContext -> WireInterfaceExpressionNode(span)

            is CSTParser.AccessorExpressionContext -> {
                val accessor = interfaceExpression.accessor()!!
                if (accessor !is CSTParser.VectorItemAccessorContext) {
                    diagnostics.reportError(ResolverDiagnosticKind.UnexpectedAccessorInInterfaceExpression, span)
                    return ErrorInterfaceExpressionNode(span, "unexpected accessor")
                }

                val vectorSizeExpression = StaticExpressionScope(this, accessor.expression()!!).ast()
                val vectoredInterface = InterfaceExpressionScope(parentScope, interfaceExpression.expression()!!).ast()
                val boundsSpecifier = VectorBoundsNode(SourceSpan.of(accessor), vectorSizeExpression)

                VectorInterfaceExpressionNode(span, vectoredInterface, boundsSpecifier)
            }

            is CSTParser.AtomExpressionContext -> {
                val atom = interfaceExpression.atom()!!
                val identifier = atom.identifier!!

                when (val declaration = resolve(identifier)) {
                    is ResolvedSymbol.Parameter -> {
                        if (declaration.ctx.type !is CSTParser.InterfaceParameterDefinitionTypeContext) {
                            diagnostics.reportError(ResolverDiagnosticKind.NotAnInterfaceTypedGenericParameter(identifier.text!!), span)
                            return ErrorInterfaceExpressionNode(span, "not an interface parameter")
                        }

                        if (atom.parameterValues() != null) {
                            diagnostics.reportError(ResolverDiagnosticKind.UnexpectedGenericInterfaceParameters(identifier.text!!), span)
                            return ErrorInterfaceExpressionNode(span, "unexpected parameters")
                        }

                        IdentifierInterfaceExpressionNode(span, identifier.toIdentifierNode())
                    }

                    is ResolvedSymbol.Interface -> {
                        val interfaceIdentifier = identifier.toIdentifierNode()
                        val parameterValues = atom.parameterValues()?.expression() ?: emptyList()
                        val parameters = GenericParameterValueScope(parentScope, parameterValues).ast()

                        DefinedInterfaceExpressionNode(span, interfaceIdentifier, parameters.interfaces, parameters.parameters)
                    }

                    null -> ErrorInterfaceExpressionNode(span, "unresolved symbol '${identifier.text}'")

                    else -> {
                        diagnostics.reportError(ResolverDiagnosticKind.CannotUseInInterfaceExpression(identifier.text!!), span)
                        ErrorInterfaceExpressionNode(span, "invalid reference in interface expression")
                    }
                }
            }

            else -> {
                diagnostics.reportError(ResolverDiagnosticKind.ExpectedInterfaceExpression, span)
                ErrorInterfaceExpressionNode(span, "expected interface expression")
            }
        }
    }
}
