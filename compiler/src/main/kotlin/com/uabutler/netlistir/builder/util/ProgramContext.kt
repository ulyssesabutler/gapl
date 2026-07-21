package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.ProgramNode
import com.uabutler.ast.node.interfaces.*
import com.uabutler.netlistir.netlist.Module

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
        node: InterfaceExpressionNode,
        definedInterfaceIdentifier: String,

        genericInterfaceValues: List<InterfaceStructure>,
        genericParameterValues: List<ParameterValue<*>>,
    ): InterfaceStructure {
        val interfaceDefinition = try {
            interfaceDefinitions[definedInterfaceIdentifier]!!.node
        } catch (_: NullPointerException) {
            throw BuilderDiagnosticException("Cannot find interface: $definedInterfaceIdentifier", node.span)
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
                ).intValueExact(),
            )
            is IdentifierInterfaceExpressionNode -> {
                try {
                    interfaceValuesContext[node.interfaceIdentifier.value]!!
                } catch (_: NullPointerException) {
                    throw BuilderDiagnosticException("Cannot find interface value: ${node.interfaceIdentifier.value}", node.span)
                }
            }
            is DefinedInterfaceExpressionNode -> buildDefinedInterface(
                node = node,
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
            is ErrorInterfaceExpressionNode -> throw IllegalStateException(
                "Reached NodeBuilder with an error node (${node.message}) that should have been caught by semantic analysis - this is a compiler bug"
            )
        }
    }

    fun buildModuleInvocationDataWithContext(
        node: InstantiationNode,

        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): Module.Invocation {

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

        return Module.Invocation(
            gaplFunctionName = node.definitionIdentifier.value,
            interfaces = interfaces,
            parameters = parameterValues,
        )
    }
}