package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.netlistir.netlist.Module

class ModuleInstantiationTracker(
    private val functionNodes: Map<String, FunctionDefinitionNode>,
    private val programContext: ProgramContext,
) {

    /* This stores the values that were used to instantiate a module, along with the input and output structures of
     * that module instantiation. The "module" itself isn't processed initially. Once it is, this field is updated.
     *
     * (We leave it null first so we can process just the module signature before it's body. This should make it simpler
     * to process recursive functions).
     */
    data class ModuleInstantiation(
        val moduleInvocation: Module.Invocation,

        val input: List<InterfaceDescription>,
        val output: List<InterfaceDescription>,

        val astNode: FunctionDefinitionNode,

        val genericInterfaceValues: Map<String, InterfaceStructure>,
        val genericParameterValues: Map<String, ParameterValue<*>>,

        var module: Module?,
    )

    // This is the main map we use to store all the modules while they're being built
    private val modules = mutableMapOf<Module.Invocation, ModuleInstantiation>()

    // Given a module instantiation, add that module to the set of modules
    fun visitModule(instantiationData: Module.Invocation): ModuleInstantiation {
        modules[instantiationData]?.let { return it }

        val astNode = try {
            functionNodes[instantiationData.gaplFunctionName]!!
        } catch (_: NullPointerException) {
            throw Exception("Unable to locate function: ${instantiationData.gaplFunctionName}")
        }

        // Match the provided values with the local identifier
        val interfaceValues =
            GenericValueMatcher.getInterfaceValues(astNode.genericInterfaces, instantiationData.interfaces)
        val parameterValues =
            GenericValueMatcher.getParameterValues(astNode.genericParameters, instantiationData.parameters)

        val input = astNode.inputFunctionIO.map {
            InterfaceDescription(
                name = it.identifier.value,
                interfaceStructure = programContext.buildInterfaceWithContext(it.interfaceExpression, interfaceValues, parameterValues),
            )
        }

        val output = astNode.outputFunctionIO.map {
            InterfaceDescription(
                name = it.identifier.value,
                interfaceStructure = programContext.buildInterfaceWithContext(it.interfaceExpression, interfaceValues, parameterValues),
            )
        }

        val moduleInstantiation = ModuleInstantiation(
            moduleInvocation = instantiationData,
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

    fun addBuiltModule(instantiation: Module.Invocation, module: Module) {
        modules[instantiation]!!.module = module
    }

    fun getModules() = modules.values.mapNotNull { it.module }.toList()
}