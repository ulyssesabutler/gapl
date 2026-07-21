package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode

class GenericArityMismatchException(val expected: Int, val actual: Int) : Exception("expected $expected, got $actual")

object GenericValueMatcher {
    fun getInterfaceValues(
        genericInterfaceDefinitionNodes: List<GenericInterfaceDefinitionNode>,
        genericInterfaceValues: List<InterfaceStructure>,
    ): Map<String, InterfaceStructure> {
        if (genericInterfaceValues.size != genericInterfaceDefinitionNodes.size) {
            throw GenericArityMismatchException(genericInterfaceDefinitionNodes.size, genericInterfaceValues.size)
        }

        return genericInterfaceDefinitionNodes
            .mapIndexed { index, it -> it.identifier.value to genericInterfaceValues[index] }
            .associate { it }
    }

    fun getParameterValues(
        genericParameterDefinitionNodes: List<GenericParameterDefinitionNode>,
        genericParameterValues: List<ParameterValue<*>>,
    ): Map<String, ParameterValue<*>> {
        if (genericParameterValues.size != genericParameterDefinitionNodes.size) {
            throw GenericArityMismatchException(genericParameterDefinitionNodes.size, genericParameterValues.size)
        }

        return genericParameterDefinitionNodes
            .mapIndexed { index, it -> it.identifier.value to genericParameterValues[index] }
            .associate { it }
        // TODO: Validate types
    }
}