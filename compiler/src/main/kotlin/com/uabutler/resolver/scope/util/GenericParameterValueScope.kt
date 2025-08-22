package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.FunctionInstantiationGenericParameterValueNode
import com.uabutler.ast.node.FunctionReferenceGenericParameterValueNode
import com.uabutler.ast.node.GenericParameterValueNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.StaticExpressionGenericParameterValueNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTAccessorExpression
import com.uabutler.cst.node.expression.CSTAdditionExpression
import com.uabutler.cst.node.expression.CSTAtomExpression
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
import com.uabutler.cst.node.expression.CSTMultiplicationExpression
import com.uabutler.cst.node.expression.CSTNotEqualsExpression
import com.uabutler.cst.node.expression.CSTParenthesizedExpression
import com.uabutler.cst.node.expression.CSTSubtractionExpression
import com.uabutler.cst.node.expression.CSTTrueExpression
import com.uabutler.cst.node.expression.CSTWireExpression
import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.cst.node.interfaces.CSTAliasInterfaceDefinition
import com.uabutler.cst.node.util.CSTFunctionGenericParameterDefinitionType
import com.uabutler.cst.node.util.CSTGenericParameterDefinition
import com.uabutler.cst.node.util.CSTNamedGenericParameterDefinitionType
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope

class GenericParameterValueScope(
    override val parentScope: Scope,
    val genericParameterValue: CSTExpression
): Scope {
    override fun resolveLocal(name: String): CSTPersistent? = null

    override fun symbols(): List<String> = emptyList()

    fun ast(): GenericParameterValueNode {
        return when (genericParameterValue) {
            is CSTTrueExpression, is CSTFalseExpression,
            is CSTIntLiteralExpression,
            is CSTParenthesizedExpression,
            is CSTMultiplicationExpression, is CSTDivisionExpression, is CSTAdditionExpression, is CSTSubtractionExpression,
            is CSTLessThanExpression, is CSTGreaterThanExpression, is CSTLessThanOrEqualsExpression, is CSTGreaterThanOrEqualsExpression,
            is CSTEqualsExpression, is CSTNotEqualsExpression,
            is CSTLogicalAndExpression, is CSTLogicalOrExpression -> {
                StaticExpressionGenericParameterValueNode(
                    value = StaticExpressionScope(parentScope, genericParameterValue).ast()
                )
            }

            is CSTAtomExpression -> { // This can be a function, or a static expression.
                val atom = genericParameterValue.atom
                when (val declaration = resolve(atom.identifier)) {
                    is CSTFunctionDefinition -> FunctionInstantiationGenericParameterValueNode(
                        instantiation = InstantiationNode(
                            definitionIdentifier = atom.identifier.toIdentifier(),
                            genericInterfaces = atom.interfaceValues.map { GenericInterfaceValueScope(this, it).ast() },
                            genericParameters = atom.parameterValues.map { GenericParameterValueScope(this, it).ast() },
                        )
                    )

                    is CSTGenericParameterDefinition -> {
                        when (declaration.type) {
                            is CSTNamedGenericParameterDefinitionType -> {
                                StaticExpressionGenericParameterValueNode(
                                    value = StaticExpressionScope(parentScope, genericParameterValue).ast()
                                )
                            }
                            is CSTFunctionGenericParameterDefinitionType -> {
                                FunctionReferenceGenericParameterValueNode(
                                    functionIdentifier = atom.identifier.toIdentifier()
                                )
                            }
                        }
                    }

                    // TODO: Remove
                    is CSTAliasInterfaceDefinition -> throw Exception("${declaration.declaredIdentifier}")

                    else -> throw Exception("Declaration of type ${declaration.let { it::class.simpleName }} not allowed in generic parameter values")
                }

            }

            is CSTAccessorExpression, is CSTWireExpression -> throw Exception("Interface expressions are not allowed in generic parameter values")
        }
    }
}