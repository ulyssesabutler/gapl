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
import com.uabutler.ast.node.functions.circuits.MemberAccessOperationNode
import com.uabutler.ast.node.functions.circuits.MultipleAccessOperationNode
import com.uabutler.ast.node.functions.circuits.ProtocolAccessorCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.RecordInterfaceConstructorExpressionNode
import com.uabutler.ast.node.functions.circuits.ReferenceCircuitExpressionNode
import com.uabutler.ast.node.functions.circuits.SingleArrayAccessOperationNode
import com.uabutler.ast.node.functions.interfaces.DefaultInterfaceTypeNode
import com.uabutler.ast.node.interfaces.VectorInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.WireInterfaceExpressionNode
import com.uabutler.cst.node.expression.CSTAccessorExpression
import com.uabutler.cst.node.expression.CSTAdditionExpression
import com.uabutler.cst.node.expression.CSTAtomExpression
import com.uabutler.cst.node.expression.CSTCircuitExpression
import com.uabutler.cst.node.expression.CSTCircuitGroupExpression
import com.uabutler.cst.node.expression.CSTCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTDeclaredCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTDivisionExpression
import com.uabutler.cst.node.expression.CSTEqualsExpression
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.expression.CSTFalseExpression
import com.uabutler.cst.node.expression.CSTGreaterThanExpression
import com.uabutler.cst.node.expression.CSTGreaterThanOrEqualsExpression
import com.uabutler.cst.node.expression.CSTIntLiteralExpression
import com.uabutler.cst.node.expression.CSTLessThanExpression
import com.uabutler.cst.node.expression.CSTLessThanOrEqualsExpression
import com.uabutler.cst.node.expression.CSTLogicalAndExpression
import com.uabutler.cst.node.expression.CSTLogicalOrExpression
import com.uabutler.cst.node.expression.CSTLoneCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTMultiplicationExpression
import com.uabutler.cst.node.expression.CSTNotEqualsExpression
import com.uabutler.cst.node.expression.CSTParenthesesCircuitNodeExpression
import com.uabutler.cst.node.expression.CSTParenthesizedExpression
import com.uabutler.cst.node.expression.CSTSubtractionExpression
import com.uabutler.cst.node.expression.CSTTrueExpression
import com.uabutler.cst.node.expression.CSTWireExpression
import com.uabutler.cst.node.expression.util.CSTBasicCircuitExpressionType
import com.uabutler.cst.node.expression.util.CSTMemberAccessor
import com.uabutler.cst.node.expression.util.CSTVectorItemAccessor
import com.uabutler.cst.node.expression.util.CSTVectorSliceAccessor
import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.cst.node.functions.CSTFunctionIO
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinition
import com.uabutler.cst.node.util.CSTGenericParameterDefinition
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope
import com.uabutler.resolver.scope.util.GenericInterfaceValueScope
import com.uabutler.resolver.scope.util.GenericParameterValueScope

class CircuitExpressionScope(
    parentScope: Scope,
    val circuitExpression: CSTCircuitExpression,
): Scope by parentScope {

    fun ast(): CircuitExpressionNode {
        return CircuitConnectionExpressionNode(
            connectedExpression = circuitExpression.connectedGroups.map { CircuitGroupExpressionScope(this, it).ast() }
        )
    }

}

class CircuitGroupExpressionScope(
    parentScope: Scope,
    val circuitGroupExpression: CSTCircuitGroupExpression,
): Scope by parentScope {

    fun ast(): CircuitGroupExpressionNode {
        return CircuitGroupExpressionNode(
            expressions = circuitGroupExpression.groupedNodes.map { CircuitNodeExpressionScope(this, it).ast() }
        )
    }

}

class CircuitNodeExpressionScope(
    parentScope: Scope,
    val body: CSTCircuitNodeExpression,
): Scope by parentScope {

    fun ast(): CircuitNodeExpressionNode {
        return when (body) {
            is CSTLoneCircuitNodeExpression -> {
                LoneCircuitExpressionScope(this, body.expression).ast()
            }
            is CSTDeclaredCircuitNodeExpression -> {
                if (body.type !is CSTBasicCircuitExpressionType) TODO("Transformers unsupported")

                val identifier = body.declaredIdentifier.toIdentifier()
                val circuitExpressionType = LoneCircuitExpressionScope(this, body.type.nodeType).ast()

                when (circuitExpressionType) {
                    is AnonymousFunctionCircuitExpressionNode -> DeclaredFunctionCircuitExpressionNode(
                        identifier = identifier,
                        instantiation = circuitExpressionType.instantiation,
                    )
                    is AnonymousGenericFunctionCircuitExpressionNode -> DeclaredGenericFunctionCircuitExpressionNode(
                        identifier = identifier,
                        functionIdentifier = circuitExpressionType.functionIdentifier,
                    )
                    is AnonymousInterfaceCircuitExpressionNode -> DeclaredInterfaceCircuitExpressionNode(
                        identifier = identifier,
                        type = circuitExpressionType.type,
                        interfaceType = circuitExpressionType.interfaceType,
                    )
                    is CircuitExpressionNodeCircuitExpression -> TODO()
                    is ProtocolAccessorCircuitExpressionNode -> TODO()
                    is RecordInterfaceConstructorExpressionNode -> TODO()
                    is ReferenceCircuitExpressionNode -> throw Exception("Attempted redeclaration of ${body.declaredIdentifier}")
                    is DeclaredFunctionCircuitExpressionNode,
                    is DeclaredGenericFunctionCircuitExpressionNode,
                    is DeclaredInterfaceCircuitExpressionNode -> throw Exception("Unexpected ${circuitExpressionType::class.simpleName}")
                }
            }
            is CSTParenthesesCircuitNodeExpression -> {
                CircuitExpressionNodeCircuitExpression(CircuitExpressionScope(this, body.expression).ast())
            }
        }
    }

}

class LoneCircuitExpressionScope(
    parentScope: Scope,
    val body: CSTExpression,
): Scope by parentScope {

    fun ast(): CircuitNodeExpressionNode {
        return when (body) {
            is CSTTrueExpression, is CSTFalseExpression, is CSTIntLiteralExpression,
            is CSTMultiplicationExpression, is CSTDivisionExpression,
            is CSTAdditionExpression, is CSTSubtractionExpression,
            is CSTLessThanExpression, is CSTGreaterThanExpression,
            is CSTLessThanOrEqualsExpression, is CSTGreaterThanOrEqualsExpression,
            is CSTEqualsExpression, is CSTNotEqualsExpression,
            is CSTLogicalAndExpression, is CSTLogicalOrExpression -> throw Exception("Expected circuit expression, got ${body::class.simpleName}")

            is CSTAccessorExpression -> { // This is either a reference or an interface expression
                when (val accessed = LoneCircuitExpressionScope(this, body.accessed).ast()) {
                    is AnonymousFunctionCircuitExpressionNode,
                    is CircuitExpressionNodeCircuitExpression,
                    is DeclaredFunctionCircuitExpressionNode,
                    is DeclaredGenericFunctionCircuitExpressionNode,
                    is DeclaredInterfaceCircuitExpressionNode, // I'm pretty sure this is impossible, but I wonder if something like declare i: wire[0] would be ambiguous?
                    is RecordInterfaceConstructorExpressionNode,
                    is AnonymousGenericFunctionCircuitExpressionNode -> throw Exception("Unexpected accessor on ${accessed::class.simpleName}")

                    is AnonymousInterfaceCircuitExpressionNode -> {
                        if (body.accessor !is CSTVectorItemAccessor) throw Exception("Unexpected accessor ${body.accessor::class.simpleName}")

                        val bounds = VectorBoundsNode(
                            boundSpecifier = StaticExpressionScope(this, body.accessor.index).ast()
                        )

                        AnonymousInterfaceCircuitExpressionNode(
                            interfaceType = accessed.interfaceType,
                            type = VectorInterfaceExpressionNode(
                                vectoredInterface = accessed.type,
                                boundsSpecifier = bounds,
                            ),
                        )
                    }

                    is ReferenceCircuitExpressionNode -> {
                        if (accessed.multipleAccess != null) throw Exception("Unexpected access operation on slice, operation not yet supported")

                        when (body.accessor) {
                            is CSTMemberAccessor -> {
                                val newAccessor = MemberAccessOperationNode(body.accessor.portIdentifier.toIdentifier())

                                ReferenceCircuitExpressionNode(
                                    identifier = accessed.identifier,
                                    singleAccesses = accessed.singleAccesses + newAccessor,
                                    multipleAccess = null,
                                )
                            }

                            is CSTVectorItemAccessor -> {
                                val newAccessor = SingleArrayAccessOperationNode(
                                    index = StaticExpressionScope(this, body.accessor.index).ast(),
                                )

                                ReferenceCircuitExpressionNode(
                                    identifier = accessed.identifier,
                                    singleAccesses = accessed.singleAccesses + newAccessor,
                                    multipleAccess = null,
                                )
                            }

                            is CSTVectorSliceAccessor -> {
                                val newAccessor = MultipleAccessOperationNode(
                                    startIndex = StaticExpressionScope(this, body.accessor.start).ast(),
                                    endIndex = StaticExpressionScope(this, body.accessor.end).ast(),
                                )

                                ReferenceCircuitExpressionNode(
                                    identifier = accessed.identifier,
                                    singleAccesses = accessed.singleAccesses,
                                    multipleAccess = newAccessor,
                                )
                            }
                        }
                    }

                    is ProtocolAccessorCircuitExpressionNode -> TODO("Protocol accessors are not supported yet")
                }
            }

            is CSTAtomExpression -> {
                val referencedNode = resolve(body.atom.identifier)

                when (referencedNode) {
                    is CSTDeclaredCircuitNodeExpression, is CSTFunctionIO -> {
                        if (body.atom.interfaceValues.isNotEmpty() || body.atom.parameterValues.isNotEmpty())
                            throw IllegalArgumentException("Unexpected use of parameters for ${body.atom.identifier} in circuit node expression")

                        ReferenceCircuitExpressionNode(
                            identifier = body.atom.identifier.toIdentifier(),
                            singleAccesses = emptyList(),
                            multipleAccess = null,
                        )
                    }
                    is CSTGenericInterfaceDefinition, is CSTInterfaceDefinition -> {
                        AnonymousInterfaceCircuitExpressionNode(
                            interfaceType = DefaultInterfaceTypeNode(),
                            type = InterfaceExpressionScope(this, body).ast(),
                        )
                    }
                    is CSTFunctionDefinition -> {
                        AnonymousFunctionCircuitExpressionNode(
                            instantiation = InstantiationNode(
                                definitionIdentifier = body.atom.identifier.toIdentifier(),
                                genericInterfaces = body.atom.interfaceValues.map { GenericInterfaceValueScope(this, it).ast() },
                                genericParameters = body.atom.parameterValues.map { GenericParameterValueScope(this, it).ast() },
                            )
                        )
                    }
                    is CSTGenericParameterDefinition -> {
                        if (body.atom.interfaceValues.isNotEmpty() || body.atom.parameterValues.isNotEmpty())
                            throw IllegalArgumentException("Unexpected use of parameters for ${body.atom.identifier} in circuit node expression")

                        AnonymousGenericFunctionCircuitExpressionNode(
                            functionIdentifier = body.atom.identifier.toIdentifier()
                        )
                    }
                    else -> throw Exception("Unexpected identifier ${body.atom.identifier} as circuit node expression, ${referencedNode::class.simpleName} found")
                }
            }

            is CSTParenthesizedExpression -> LoneCircuitExpressionScope(this, body.expression).ast()

            CSTWireExpression -> {
                return AnonymousInterfaceCircuitExpressionNode(
                    interfaceType = DefaultInterfaceTypeNode(),
                    type = WireInterfaceExpressionNode(),
                )
            }
        }
    }

}