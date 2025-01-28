package com.uabutler.v2.gaplir.builder.util

import com.uabutler.v2.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.v2.ast.node.GenericParameterDefinitionNode
import com.uabutler.v2.gaplir.InterfaceStructure

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
        genericParameterValues: List<Int>, // TODO: This could be any value
    ): Map<String, Int> {
        return genericParameterDefinitionNodes
            .mapIndexed { index, it -> it.identifier.value to genericParameterValues[index] }
            .associate { it }
        // TODO: Validate types
    }
}