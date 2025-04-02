package com.uabutler.gaplir.builder.util

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.Module

class ModuleInstantiationTracker(
    private val functionNodes: Map<String, FunctionDefinitionNode>,
    private val programContext: ProgramContext,
) {

    data class ModuleInstantiationData(
        val functionIdentifier: String,
        val genericInterfaceValues: List<InterfaceStructure>,
        val genericParameterValues: List<Int>, // TODO: This could be any value
    )

    /* This stores the values that were used to instantiate a module, along with the input and output structures of
     * that module instantiation. The "module" itself isn't processed initially. Once it is, this field is updated.
     *
     * (We leave it null first so we can process just the module signature before it's body. This should make it simpler
     * to process recursive functions).
     */
    data class ModuleInstantiation(
        val moduleInstantiationData: ModuleInstantiationData,

        val input: Map<String, InterfaceStructure>,
        val output: Map<String, InterfaceStructure>,

        val astNode: FunctionDefinitionNode,

        val genericInterfaceValues: Map<String, InterfaceStructure>,
        val genericParameterValues: Map<String, Int>, // TODO: This could be any value

        var module: Module?,
    )

    // This is the main map we use to store all the modules while they're being built
    private val modules = mutableMapOf<ModuleInstantiationData, ModuleInstantiation>()

    // Given a module instantiation, add that module to the set of modules
    fun visitModule(instantiationData: ModuleInstantiationData): ModuleInstantiation {
        modules[instantiationData]?.let { return it }

        val astNode = functionNodes[instantiationData.functionIdentifier]!!

        // Match the provided values with the local identifier
        val interfaceValues =
            GenericValueMatcher.getInterfaceValues(astNode.genericInterfaces, instantiationData.genericInterfaceValues)
        val parameterValues =
            GenericValueMatcher.getParameterValues(astNode.genericParameters, instantiationData.genericParameterValues)

        val input = astNode.inputFunctionIO.associate {
            it.identifier.value to programContext.buildInterfaceWithContext(it.interfaceType, interfaceValues, parameterValues)
        }

        val output = astNode.outputFunctionIO.associate {
            it.identifier.value to programContext.buildInterfaceWithContext(it.interfaceType, interfaceValues, parameterValues)
        }

        val moduleInstantiation = ModuleInstantiation(
            moduleInstantiationData = instantiationData,
            input = input,
            output = output,
            astNode = astNode,
            genericInterfaceValues = interfaceValues,
            genericParameterValues = parameterValues,
            module = null,
        )

        modules[instantiationData] = moduleInstantiation

        return moduleInstantiation
    }

    fun getUnbuiltModules() = modules.filter { it.value.module == null }.values

    fun addBuiltModule(instantiation: ModuleInstantiationData, module: Module) {
        modules[instantiation]!!.module = module
    }

    fun getModules() = modules.values.mapNotNull { it.module }.toList()
}