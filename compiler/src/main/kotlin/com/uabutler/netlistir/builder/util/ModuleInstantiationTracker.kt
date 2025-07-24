package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.netlistir.netlist.Module
import com.uabutler.util.InterfaceType

class ModuleInstantiationTracker(
    private val functionNodes: Map<String, FunctionDefinitionNode>,
    private val programContext: ProgramContext,
) {

    /* This is a data class that stores all of the data needed to instantiate any module. That is, it contains enough
     * information to find the definition (using the identifier), and it contains the values for each parameter.
     *
     * Each unique set of module instantiation data will correspond to one verilog module. That said, depending on how
     * much resource sharing is possible, it might correspond to multiple verilog instantiations.
     *
     * We might need to modify this if we want to support something like function overloading in the future.
     */
    data class ModuleInstantiationData(
        val functionIdentifier: String,
        val genericInterfaceValues: List<InterfaceStructure>,
        val genericParameterValues: List<ParameterValue<*>>,
    )

    /* This stores the values that were used to instantiate a module, along with the input and output structures of
     * that module instantiation. The "module" itself isn't processed initially. Once it is, this field is updated.
     *
     * (We leave it null first so we can process just the module signature before it's body. This should make it simpler
     * to process recursive functions).
     */
    data class ModuleInstantiation(
        val moduleInstantiationData: ModuleInstantiationData,

        val input: List<InterfaceDescription>,
        val output: List<InterfaceDescription>,

        val astNode: FunctionDefinitionNode,

        val genericInterfaceValues: Map<String, InterfaceStructure>,
        val genericParameterValues: Map<String, ParameterValue<*>>,

        var module: Module?,
    )

    // This is the main map we use to store all the modules while they're being built
    private val modules = mutableMapOf<ModuleInstantiationData, ModuleInstantiation>()

    // Given a module instantiation, add that module to the set of modules
    fun visitModule(instantiationData: ModuleInstantiationData): ModuleInstantiation {
        modules[instantiationData]?.let { return it }

        val astNode = try {
            functionNodes[instantiationData.functionIdentifier]!!
        } catch (e: NullPointerException) {
            throw Exception("Unable to locate function: ${instantiationData.functionIdentifier}")
        }

        // Match the provided values with the local identifier
        val interfaceValues =
            GenericValueMatcher.getInterfaceValues(astNode.genericInterfaces, instantiationData.genericInterfaceValues)
        val parameterValues =
            GenericValueMatcher.getParameterValues(astNode.genericParameters, instantiationData.genericParameterValues)

        val input = astNode.inputFunctionIO.map {
            InterfaceDescription(
                name = it.identifier.value,
                interfaceStructure = programContext.buildInterfaceWithContext(it.interfaceExpression, interfaceValues, parameterValues),
                interfaceType = InterfaceType.fromInterfaceTypeNode(it.interfaceType)
            )
        }

        val output = astNode.outputFunctionIO.map {
            InterfaceDescription(
                name = it.identifier.value,
                interfaceStructure = programContext.buildInterfaceWithContext(it.interfaceExpression, interfaceValues, parameterValues),
                interfaceType = InterfaceType.fromInterfaceTypeNode(it.interfaceType)
            )
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