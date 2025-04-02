package com.uabutler.gaplir.builder.util

import com.uabutler.ast.node.ProgramNode
import com.uabutler.ast.node.interfaces.*
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.RecordInterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure
import com.uabutler.gaplir.builder.definition.AbstractInterfaceDefinition
import com.uabutler.gaplir.builder.signature.InterfaceSignature

class ProgramContext(val program: ProgramNode) {
    private val interfaceSignatures = program.interfaces
        .map { InterfaceSignature.fromNode(it) }
        .associateBy { it.identifier }
    private val interfaceDefinitions = program.interfaces
        .map { AbstractInterfaceDefinition.fromNode(it) }
        .associateBy { it.signature.identifier }

    /* TODO: Do we need this?
    private val moduleSignatures = program.functions
        .map { ModuleSignature.fromNode(it) }
        .associateBy { it.identifier }
     */

    private fun buildDefinedInterface(
        definedInterfaceIdentifier: String,

        genericInterfaceValues: List<InterfaceStructure>,
        genericParameterValues: List<Int>, // TODO: This could be any value
    ): InterfaceStructure {
        val interfaceDefinition = interfaceDefinitions[definedInterfaceIdentifier]!!.node

        // Match the provided values with the local identifier
        val interfaceValues =
            GenericValueMatcher.getInterfaceValues(interfaceDefinition.genericInterfaces, genericInterfaceValues)
        val parameterValues =
            GenericValueMatcher.getParameterValues(interfaceDefinition.genericParameters, genericParameterValues)

        return when (interfaceDefinition) {
            is AliasInterfaceDefinitionNode -> buildInterfaceWithContext(
                node = interfaceDefinition.aliasedInterface,
                interfaceValuesContext = interfaceValues,
                parameterValuesContext = parameterValues,
            )
            is RecordInterfaceDefinitionNode -> {
                // TODO: Inherits
                val ports = interfaceDefinition.ports.map {
                    it.identifier.value to it.type
                }
                    .associate { it }
                    .mapValues {
                        buildInterfaceWithContext(
                            node = it.value,
                            interfaceValuesContext = interfaceValues,
                            parameterValuesContext = parameterValues,
                        )
                    }

                RecordInterfaceStructure(ports)
            }
        }
        // TODO: Validation, types and number
    }

    fun buildInterfaceWithContext(
        node: InterfaceExpressionNode,

        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
    ): InterfaceStructure {
        return when (node) {
            is WireInterfaceExpressionNode -> WireInterfaceStructure
            is VectorInterfaceExpressionNode -> VectorInterfaceStructure(
                vectoredInterface = buildInterfaceWithContext(
                    node = node.vectoredInterface,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                ),
                size = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                    staticExpression = node.boundsSpecifier.boundSpecifier,
                    context = parameterValuesContext,
                ),
            )
            is IdentifierInterfaceExpressionNode -> interfaceValuesContext[node.interfaceIdentifier.value]!!
            is DefinedInterfaceExpressionNode -> buildDefinedInterface(
                definedInterfaceIdentifier = node.interfaceIdentifier.value,
                genericInterfaceValues = node.genericInterfaces.map {
                    buildInterfaceWithContext(
                        node = it.value,
                        interfaceValuesContext = interfaceValuesContext,
                        parameterValuesContext = parameterValuesContext,
                    )
                },
                genericParameterValues = node.genericParameters.map {
                    StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                        staticExpression = it.value,
                        context = parameterValuesContext,
                    )
                },
            )
        }
    }
}