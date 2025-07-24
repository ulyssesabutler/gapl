package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.ProgramNode
import com.uabutler.ast.node.interfaces.*

class ProgramContext(program: ProgramNode) {

    private val interfaceDefinitions = program.interfaces
        .map { AbstractInterfaceDefinition.fromNode(it) }
        .associateBy { it.signature.identifier }

    /* TODO: We should eventually use this for validation
    private val interfaceSignatures = program.interfaces
        .map { InterfaceSignature.fromNode(it) }
        .associateBy { it.identifier }
    private val moduleSignatures = program.functions
        .map { ModuleSignature.fromNode(it) }
        .associateBy { it.identifier }
     */

    private fun buildDefinedInterface(
        definedInterfaceIdentifier: String,

        genericInterfaceValues: List<InterfaceStructure>,
        genericParameterValues: List<ParameterValue<*>>,
    ): InterfaceStructure {
        val interfaceDefinition = try {
            interfaceDefinitions[definedInterfaceIdentifier]!!.node
        } catch (_: NullPointerException) {
            throw Exception("Cannot find interface:  $definedInterfaceIdentifier")
        }

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
        parameterValuesContext: Map<String, ParameterValue<*>>,
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
            is IdentifierInterfaceExpressionNode -> {
                try {
                    interfaceValuesContext[node.interfaceIdentifier.value]!!
                } catch (_: NullPointerException) {
                    throw Exception("Cannot find interface value: ${node.interfaceIdentifier.value}")
                }
            }
            is DefinedInterfaceExpressionNode -> buildDefinedInterface(
                definedInterfaceIdentifier = node.interfaceIdentifier.value,
                genericInterfaceValues = node.genericInterfaces.map {
                    buildInterfaceWithContext(
                        node = it.value,
                        interfaceValuesContext = interfaceValuesContext,
                        parameterValuesContext = parameterValuesContext,
                    )
                },
                genericParameterValues = node.genericParameters.map { ParameterValue.fromNode(it, this, interfaceValuesContext, parameterValuesContext) },
            )
        }
    }

    fun buildFlatInterfaceWithContext(
        node: InterfaceExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): List<FlatInterfaceWireVector> {
        return InterfaceFlattener.fromGAPLInterfaceStructure(
            buildInterfaceWithContext(
                node = node,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        )
    }

    fun buildModuleInstantiationDataWithContext(
        node: InstantiationNode,

        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): ModuleInstantiationTracker.ModuleInstantiationData {

        // This supports positional arguments
        val interfaces = node.genericInterfaces.map {
            buildInterfaceWithContext(
                node = it.value,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        }

        // This supports positional arguments
        val parameterValues = node.genericParameters.map {
            ParameterValue.fromNode(
                node = it,
                programContext = this,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        }

        return ModuleInstantiationTracker.ModuleInstantiationData(
            functionIdentifier = node.definitionIdentifier.value,
            genericInterfaceValues = interfaces,
            genericParameterValues = parameterValues,
        )
    }
}