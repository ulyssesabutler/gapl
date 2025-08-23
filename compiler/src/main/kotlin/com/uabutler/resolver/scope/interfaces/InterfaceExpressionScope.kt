package com.uabutler.resolver.scope.interfaces

import com.uabutler.ast.node.VectorBoundsNode
import com.uabutler.ast.node.interfaces.DefinedInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.IdentifierInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.ast.node.interfaces.VectorInterfaceExpressionNode
import com.uabutler.ast.node.interfaces.WireInterfaceExpressionNode
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
import com.uabutler.cst.node.expression.util.CSTVectorItemAccessor
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition
import com.uabutler.cst.node.util.CSTInterfaceParameterDefinitionType
import com.uabutler.cst.node.util.CSTParameterDefinition
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.staticexpressions.StaticExpressionScope
import com.uabutler.resolver.scope.util.GenericParameterValueScope

class InterfaceExpressionScope(
    override val parentScope: Scope,
    val interfaceExpression: CSTExpression
): Scope by parentScope {

    fun ast(): InterfaceExpressionNode {
        return when (interfaceExpression) {
            is CSTWireExpression -> WireInterfaceExpressionNode()

            is CSTAccessorExpression -> {
                val vector = interfaceExpression.accessor
                if (vector !is CSTVectorItemAccessor) throw IllegalArgumentException("Unexpected accessor $vector")

                val vectorSizeExpression = StaticExpressionScope(this, interfaceExpression.accessor.index).ast()

                val vectoredInterface = InterfaceExpressionScope(parentScope, interfaceExpression.accessed).ast()
                val boundsSpecifier = VectorBoundsNode(vectorSizeExpression)

                VectorInterfaceExpressionNode(vectoredInterface, boundsSpecifier)
            }

            is CSTAtomExpression -> {
                val atom = interfaceExpression.atom

                when (val declarationNode = resolve(atom.identifier)) {

                    is CSTParameterDefinition -> {
                        if (declarationNode.type !is CSTInterfaceParameterDefinitionType)
                            throw Exception("Unexpected type for interface expression, got ${declarationNode.type::class.simpleName}")

                        if (atom.parameterValues.isNotEmpty())
                            throw Exception("Unexpected parameters for generic interface")

                        IdentifierInterfaceExpressionNode(atom.identifier.toIdentifier())
                    }

                    is CSTInterfaceDefinition -> {
                        val interfaceIdentifier = atom.identifier.toIdentifier()
                        val parameters = GenericParameterValueScope(parentScope, atom.parameterValues).ast()

                        DefinedInterfaceExpressionNode(interfaceIdentifier, parameters.interfaces, parameters.parameters)
                    }

                    else -> throw Exception("Cannot use identifier for ${declarationNode::class.simpleName} in interface expression")
                }
            }

            is CSTTrueExpression, is CSTFalseExpression,
            is CSTIntLiteralExpression,
            is CSTMultiplicationExpression, is CSTDivisionExpression, is CSTAdditionExpression, is CSTSubtractionExpression,
            is CSTLessThanExpression, is CSTGreaterThanExpression,
            is CSTLessThanOrEqualsExpression, is CSTGreaterThanOrEqualsExpression,
            is CSTEqualsExpression, is CSTNotEqualsExpression,
            is CSTLogicalAndExpression, is CSTLogicalOrExpression,
            is CSTParenthesizedExpression -> throw Exception("Expected interface expression, got ${interfaceExpression::class.simpleName}")
        }
    }
}