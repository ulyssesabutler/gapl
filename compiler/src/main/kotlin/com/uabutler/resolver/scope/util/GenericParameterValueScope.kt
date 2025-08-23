package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.FunctionInstantiationGenericParameterValueNode
import com.uabutler.ast.node.FunctionReferenceGenericParameterValueNode
import com.uabutler.ast.node.GenericInterfaceValueNode
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
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition
import com.uabutler.cst.node.util.CSTFunctionParameterDefinitionType
import com.uabutler.cst.node.util.CSTIntegerParameterDefinitionType
import com.uabutler.cst.node.util.CSTInterfaceParameterDefinitionType
import com.uabutler.cst.node.util.CSTParameterDefinition
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.interfaces.InterfaceExpressionScope
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope

class GenericParameterValueScope(
    override val parentScope: Scope,
    val genericParameterValues: List<CSTExpression>,
): Scope {
    override fun resolveLocal(name: String): CSTPersistent? = null

    override fun symbols(): List<String> = emptyList()

    // Horrible, hacky bridge between the CST's lack of caring about a parameter type and the AST very much caring.
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

    private fun GenericInterfaceValueNode.toParameterValue(): ParameterValue {
        return ParameterValue(singleInterface = this)
    }

    private fun GenericParameterValueNode.toParameterValue(): ParameterValue {
        return ParameterValue(singleParameter = this)
    }

    private fun singleParameter(value: CSTExpression): ParameterValue {
       return when (value) {
            is CSTTrueExpression, is CSTFalseExpression,
            is CSTIntLiteralExpression,
            is CSTParenthesizedExpression,
            is CSTMultiplicationExpression, is CSTDivisionExpression, is CSTAdditionExpression, is CSTSubtractionExpression,
            is CSTLessThanExpression, is CSTGreaterThanExpression, is CSTLessThanOrEqualsExpression, is CSTGreaterThanOrEqualsExpression,
            is CSTEqualsExpression, is CSTNotEqualsExpression,
            is CSTLogicalAndExpression, is CSTLogicalOrExpression -> {
                StaticExpressionGenericParameterValueNode(
                    value = StaticExpressionScope(parentScope, value).ast()
                ).toParameterValue()
            }

            is CSTAtomExpression -> { // This can be a function, or a static expression.
                val atom = value.atom
                when (val declaration = resolve(atom.identifier)) {
                    is CSTFunctionDefinition -> {
                        val parameters = GenericParameterValueScope(this, atom.parameterValues).ast()
                        FunctionInstantiationGenericParameterValueNode(
                            instantiation = InstantiationNode(
                                definitionIdentifier = atom.identifier.toIdentifier(),
                                genericInterfaces = parameters.interfaces,
                                genericParameters = parameters.parameters,
                            )
                        ).toParameterValue()
                    }

                    is CSTParameterDefinition -> {
                        when (declaration.type) {
                            is CSTIntegerParameterDefinitionType -> {
                                StaticExpressionGenericParameterValueNode(
                                    value = StaticExpressionScope(parentScope, value).ast()
                                ).toParameterValue()
                            }
                            is CSTFunctionParameterDefinitionType -> {
                                FunctionReferenceGenericParameterValueNode(
                                    functionIdentifier = atom.identifier.toIdentifier()
                                ).toParameterValue()
                            }
                            is CSTInterfaceParameterDefinitionType -> GenericInterfaceValueNode(
                                value = InterfaceExpressionScope(this, value).ast()
                            ).toParameterValue()
                        }
                    }

                    is CSTInterfaceDefinition -> GenericInterfaceValueNode(
                        value = InterfaceExpressionScope(this, value).ast()
                    ).toParameterValue()

                    else -> throw Exception("Declaration of type ${declaration.let { it::class.simpleName }} not allowed in generic parameter values")
                }

            }

            is CSTAccessorExpression, is CSTWireExpression -> GenericInterfaceValueNode(
                value = InterfaceExpressionScope(this, value).ast()
            ).toParameterValue()
        }
    }
}