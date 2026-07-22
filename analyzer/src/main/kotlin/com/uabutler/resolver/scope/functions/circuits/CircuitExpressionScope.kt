package com.uabutler.resolver.scope.functions.circuits

import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.VectorBoundsNode
import com.uabutler.ast.node.functions.circuits.AnonymousFunctionCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.AnonymousGenericFunctionCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.AnonymousInterfaceCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.CircuitConnectionExpressionNode
import com.uabutler.ast.node.functions.circuits.CircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.CircuitExpressionNodeCircuitExpression
import com.uabutler.ast.node.functions.circuits.CircuitGroupExpressionNode
import com.uabutler.ast.node.functions.circuits.CircuitNodeExpressionNode
import com.uabutler.ast.node.functions.circuits.DeclaredFunctionCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.DeclaredGenericFunctionCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.DeclaredInterfaceCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.ErrorCircuitNodeExpressionNode
import com.uabutler.ast.node.functions.circuits.MemberAccessOperationNode
import com.uabutler.ast.node.functions.circuits.MultipleAccessOperationNode
import com.uabutler.ast.node.functions.circuits.ProtocolAccessorCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.RecordInterfaceConstructorExpressionNode
import com.uabutler.ast.node.functions.circuits.ReferenceCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.SingleArrayAccessOperationNode
import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.ast.node.interfaces.VectorInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.WireInterfaceExpressionNode
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope
import com.uabutler.resolver.scope.util.GenericParameterValueScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class CircuitExpressionScope(
    parentScope: Scope,
    val circuitExpression: CSTParser.CircuitExpressionContext,
): Scope by parentScope {

    fun ast(): CircuitExpressionNode {
        return CircuitConnectionExpressionNode(
            span = SourceSpan.of(circuitExpression),
            connectedExpression = circuitExpression.circuitGroupExpression().map { CircuitGroupExpressionScope(this, it).ast() }
        )
    }

}

class CircuitGroupExpressionScope(
    parentScope: Scope,
    val circuitGroupExpression: CSTParser.CircuitGroupExpressionContext,
): Scope by parentScope {

    fun ast(): CircuitGroupExpressionNode {
        return CircuitGroupExpressionNode(
            span = SourceSpan.of(circuitGroupExpression),
            expressions = circuitGroupExpression.circuitNodeExpression().map { CircuitNodeExpressionScope(this, it).ast() }
        )
    }

}

class CircuitNodeExpressionScope(
    parentScope: Scope,
    val body: CSTParser.CircuitNodeExpressionContext,
): Scope by parentScope {

    fun ast(): CircuitNodeExpressionNode {
        val span = SourceSpan.of(body)

        return when (body) {
            is CSTParser.LoneCircuitExpressionContext -> {
                LoneCircuitExpressionScope(this, body.expression()!!).ast()
            }
            is CSTParser.DeclaredCircuitExpressionContext -> {
                val identifier = body.declaredIdentifier!!.toIdentifierNode()
                val type = body.circuitExpressionType()

                if (type !is CSTParser.BasicCircuitExpressionTypeContext) {
                    diagnostics.reportError(ResolverDiagnosticKind.UnsupportedTransformer, span)
                    return ErrorCircuitNodeExpressionNode(span, "unsupported transformer")
                }

                when (val circuitExpressionType = LoneCircuitExpressionScope(this, type.expression()!!).ast()) {
                    is AnonymousFunctionCircuitExpressionNode -> DeclaredFunctionCircuitExpressionNode(
                        span, identifier, circuitExpressionType.instantiation,
                    )
                    is AnonymousGenericFunctionCircuitExpressionNode -> DeclaredGenericFunctionCircuitExpressionNode(
                        span, identifier, circuitExpressionType.functionIdentifier,
                    )
                    is AnonymousInterfaceCircuitExpressionNode -> DeclaredInterfaceCircuitExpressionNode(
                        span, identifier, circuitExpressionType.interfaceType, circuitExpressionType.type,
                    )
                    is ErrorCircuitNodeExpressionNode -> circuitExpressionType
                    else -> {
                        diagnostics.reportError(ResolverDiagnosticKind.InvalidDeclarationType(identifier.value), span)
                        ErrorCircuitNodeExpressionNode(span, "invalid declaration type")
                    }
                }
            }
            is CSTParser.ParenCircuitExpressionContext -> {
                CircuitExpressionNodeCircuitExpression(span, CircuitExpressionScope(this, body.circuitExpression()!!).ast())
            }
            else -> throw IllegalStateException("Unexpected circuit node expression context $body")
        }
    }

}

class LoneCircuitExpressionScope(
    parentScope: Scope,
    val body: CSTParser.ExpressionContext,
): Scope by parentScope {

    fun ast(): CircuitNodeExpressionNode {
        val span = SourceSpan.of(body)

        return when (body) {
            is CSTParser.TrueExpressionContext, is CSTParser.FalseExpressionContext, is CSTParser.LiteralExpressionContext,
            is CSTParser.MultiplicaitonExpressionContext, is CSTParser.AdditionExpressionContext,
            is CSTParser.RelationalExpressionContext, is CSTParser.EqualityExpressionContext,
            is CSTParser.LogicalAndExpressionContext, is CSTParser.LogicalOrExpressionContext -> {
                diagnostics.reportError(ResolverDiagnosticKind.ExpectedCircuitExpressionGotValueExpression, span)
                ErrorCircuitNodeExpressionNode(span, "expected circuit expression")
            }

            is CSTParser.AccessorExpressionContext -> { // This is either a reference or an interface expression
                when (val accessed = LoneCircuitExpressionScope(this, body.expression()!!).ast()) {
                    is ErrorCircuitNodeExpressionNode -> accessed

                    is AnonymousFunctionCircuitExpressionNode,
                    is CircuitExpressionNodeCircuitExpression,
                    is DeclaredFunctionCircuitExpressionNode,
                    is DeclaredGenericFunctionCircuitExpressionNode,
                    is DeclaredInterfaceCircuitExpressionNode,
                    is RecordInterfaceConstructorExpressionNode,
                    is AnonymousGenericFunctionCircuitExpressionNode -> {
                        diagnostics.reportError(ResolverDiagnosticKind.UnexpectedAccessorOnCircuitExpression, span)
                        ErrorCircuitNodeExpressionNode(span, "unexpected accessor")
                    }

                    is AnonymousInterfaceCircuitExpressionNode -> {
                        val accessor = body.accessor()!!
                        if (accessor !is CSTParser.VectorItemAccessorContext) {
                            diagnostics.reportError(ResolverDiagnosticKind.UnexpectedAccessorOnAnonymousInterface, span)
                            return ErrorCircuitNodeExpressionNode(span, "unexpected accessor")
                        }

                        val bounds = VectorBoundsNode(
                            span = SourceSpan.of(accessor),
                            boundSpecifier = StaticExpressionScope(this, accessor.expression()!!).ast(),
                        )

                        AnonymousInterfaceCircuitExpressionNode(
                            span = span,
                            interfaceType = accessed.interfaceType,
                            type = VectorInterfaceExpressionNode(span, accessed.type, bounds),
                        )
                    }

                    is ReferenceCircuitExpressionNode -> {
                        if (accessed.multipleAccess != null) {
                            diagnostics.reportError(ResolverDiagnosticKind.UnsupportedSliceAccess, span)
                            return ErrorCircuitNodeExpressionNode(span, "access on slice not supported")
                        }

                        when (val accessor = body.accessor()!!) {
                            is CSTParser.MemberAccessorContext -> {
                                val newAccessor = MemberAccessOperationNode(SourceSpan.of(accessor), accessor.portIdentifier!!.toIdentifierNode())
                                ReferenceCircuitExpressionNode(span, accessed.identifier, accessed.singleAccesses + newAccessor, null)
                            }

                            is CSTParser.VectorItemAccessorContext -> {
                                val newAccessor = SingleArrayAccessOperationNode(
                                    SourceSpan.of(accessor),
                                    StaticExpressionScope(this, accessor.expression()!!).ast(),
                                )
                                ReferenceCircuitExpressionNode(span, accessed.identifier, accessed.singleAccesses + newAccessor, null)
                            }

                            is CSTParser.VectorSliceAccessorContext -> {
                                val newAccessor = MultipleAccessOperationNode(
                                    SourceSpan.of(accessor),
                                    StaticExpressionScope(this, accessor.startIndex!!).ast(),
                                    StaticExpressionScope(this, accessor.endIndex!!).ast(),
                                )
                                ReferenceCircuitExpressionNode(span, accessed.identifier, accessed.singleAccesses, newAccessor)
                            }

                            else -> throw IllegalStateException("Unexpected accessor context $accessor")
                        }
                    }

                    is ProtocolAccessorCircuitExpressionNode -> {
                        diagnostics.reportError(ResolverDiagnosticKind.UnsupportedProtocolAccessor, span)
                        ErrorCircuitNodeExpressionNode(span, "protocol accessors not supported")
                    }
                }
            }

            is CSTParser.AtomExpressionContext -> {
                val atom = body.atom()!!
                val identifier = atom.identifier!!

                when (val referencedNode = resolve(identifier)) {
                    is ResolvedSymbol.CircuitNode, is ResolvedSymbol.FunctionIO -> {
                        if (atom.parameterValues() != null) {
                            diagnostics.reportError(ResolverDiagnosticKind.UnexpectedCircuitNodeParameters(identifier.text!!), span)
                            return ErrorCircuitNodeExpressionNode(span, "unexpected parameters")
                        }

                        ReferenceCircuitExpressionNode(span, identifier.toIdentifierNode(), emptyList(), null)
                    }

                    is ResolvedSymbol.Interface -> {
                        AnonymousInterfaceCircuitExpressionNode(
                            span = span,
                            interfaceType = DefaultInterfaceTypeNode(span),
                            type = InterfaceExpressionScope(this, body).ast(),
                        )
                    }

                    is ResolvedSymbol.Function -> {
                        val parameterValues = atom.parameterValues()?.expression() ?: emptyList()
                        val parameters = GenericParameterValueScope(this, parameterValues).ast()

                        AnonymousFunctionCircuitExpressionNode(
                            span = span,
                            instantiation = InstantiationNode(
                                span = span,
                                definitionIdentifier = identifier.toIdentifierNode(),
                                genericInterfaces = parameters.interfaces,
                                genericParameters = parameters.parameters,
                            )
                        )
                    }

                    is ResolvedSymbol.Parameter -> {
                        if (referencedNode.ctx.type is CSTParser.InterfaceParameterDefinitionTypeContext) {
                            AnonymousInterfaceCircuitExpressionNode(
                                span = span,
                                interfaceType = DefaultInterfaceTypeNode(span),
                                type = InterfaceExpressionScope(this, body).ast(),
                            )
                        } else {
                            if (atom.parameterValues() != null) {
                                diagnostics.reportError(ResolverDiagnosticKind.UnexpectedCircuitNodeParameters(identifier.text!!), span)
                                return ErrorCircuitNodeExpressionNode(span, "unexpected parameters")
                            }

                            AnonymousGenericFunctionCircuitExpressionNode(span, identifier.toIdentifierNode())
                        }
                    }

                    null -> ErrorCircuitNodeExpressionNode(span, "unresolved symbol '${identifier.text}'")
                }
            }

            is CSTParser.ParenExpressionContext -> LoneCircuitExpressionScope(this, body.expression()!!).ast()

            is CSTParser.WireExpressionContext -> AnonymousInterfaceCircuitExpressionNode(
                span = span,
                interfaceType = DefaultInterfaceTypeNode(span),
                type = WireInterfaceExpressionNode(span),
            )

            else -> throw IllegalStateException("Unexpected expression context $body")
        }
    }

}
