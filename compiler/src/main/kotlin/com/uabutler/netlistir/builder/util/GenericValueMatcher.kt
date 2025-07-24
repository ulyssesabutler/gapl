package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode

object GenericValueMatcher {
    fun getInterfaceValues(
        genericInterfaceDefinitionNodes: List<GenericInterfaceDefinitionNode>,
        genericInterfaceValues: List<InterfaceStructure>,
    ): Map<String, InterfaceStructure> {
        return genericInterfaceDefinitionNodes
            .mapIndexed { index, it -> it.identifier.value to genericInterfaceValues[index] }
            .associate { it }
    }

    fun getParameterValues(
        genericParameterDefinitionNodes: List<GenericParameterDefinitionNode>,
        genericParameterValues: List<ParameterValue<*>>,
    ): Map<String, ParameterValue<*>> {
        return genericParameterDefinitionNodes
            .mapIndexed { index, it -> it.identifier.value to genericParameterValues[index] }
            .associate { it }
        // TODO: Validate types
    }
}