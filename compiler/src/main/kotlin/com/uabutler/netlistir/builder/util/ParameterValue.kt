package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.FunctionInstantiationGenericParameterValueNode
import com.uabutler.ast.node.FunctionReferenceGenericParameterValueNode
import com.uabutler.ast.node.GenericParameterValueNode
import com.uabutler.ast.node.StaticExpressionGenericParameterValueNode


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

                is FunctionInstantiationGenericParameterValueNode -> {
                    FunctionInstantiationParameterValue(
                        programContext.buildModuleInstantiationDataWithContext(
                            node = node.instantiation,
                            interfaceValuesContext = interfaceValuesContext,
                            parameterValuesContext = parameterValuesContext,
                        )
                    )
                }

                is FunctionReferenceGenericParameterValueNode -> {
                    parameterValuesContext[node.functionIdentifier.value]!!
                }
            }
        }
    }
}

data class IntegerParameterValue(
    override val value: Int
): ParameterValue<Int>

data class FunctionInstantiationParameterValue(
    override val value: ModuleInstantiationTracker.ModuleInstantiationData
): ParameterValue<ModuleInstantiationTracker.ModuleInstantiationData>