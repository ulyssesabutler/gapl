package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.FunctionExpressionParameterValueNode
import com.uabutler.ast.node.GenericParameterValueNode
import com.uabutler.ast.node.StaticExpressionGenericParameterValueNode
import com.uabutler.ast.node.functions.FunctionExpressionInstantiationNode
import com.uabutler.ast.node.functions.FunctionExpressionReferenceNode
import com.uabutler.netlistir.netlist.Module


sealed interface ParameterValue<T> {
    val value: T

    companion object {
        fun fromNode(
            node: GenericParameterValueNode,

            programContext: ProgramContext,

            interfaceValuesContext: Map<String, InterfaceStructure>,
            parameterValuesContext: Map<String, ParameterValue<*>>,
        ): ParameterValue<*> {
            return when (node) {
                is StaticExpressionGenericParameterValueNode -> {
                    IntegerParameterValue(
                        StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                            staticExpression = node.value,
                            context = parameterValuesContext,
                        )
                    )
                }

                is FunctionExpressionParameterValueNode -> {
                    when (node.functionExpression) {
                        is FunctionExpressionInstantiationNode -> {
                            FunctionInstantiationParameterValue(
                                programContext.buildModuleInvocationDataWithContext(
                                    node = node.functionExpression.instantiation,
                                    interfaceValuesContext = interfaceValuesContext,
                                    parameterValuesContext = parameterValuesContext,
                                )
                            )
                        }
                        is FunctionExpressionReferenceNode -> {
                            parameterValuesContext[node.functionExpression.identifier.value]!!
                        }
                    }
                }
            }
        }
    }
}

data class IntegerParameterValue(
    override val value: Int
): ParameterValue<Int>

data class FunctionInstantiationParameterValue(
    override val value: Module.Invocation
): ParameterValue<Module.Invocation>