package com.uabutler.gaplir.builder

import com.uabutler.ast.node.ProgramNode
import com.uabutler.gaplir.Module
import com.uabutler.gaplir.builder.util.ModuleInstantiationTracker
import com.uabutler.gaplir.builder.util.ProgramContext
import com.uabutler.gaplir.util.ModuleInvocation

class ModuleBuilder(val program: ProgramNode) {

    private val programContext = ProgramContext(program)
    private val functionNodes = program.functions
        .associateBy { it.identifier.value }

    private val moduleInstantiationTracker = ModuleInstantiationTracker(
        programContext = programContext,
        functionNodes = functionNodes,
    )

    private val nodeBuilder = NodeBuilder(
        programContext = programContext,
        moduleInstantiationTracker = moduleInstantiationTracker,
    )

    fun buildAllModules(): List<Module> {
        /* First, add all concrete modules to the set of modules. These are modules that don't have any
         * generic parameters. These modules can be built without any additional information or context. To build
         * any other module, we need to know the actual parameter values.
         */
        program.functions
            .filter { it.genericInterfaces.isEmpty() && it.genericParameters.isEmpty() }
            .forEach {
                moduleInstantiationTracker.visitModule(
                    ModuleInstantiationTracker.ModuleInstantiationData(
                        functionIdentifier = it.identifier.value,
                        genericInterfaceValues = emptyList(),
                        genericParameterValues = emptyList(),
                    )
                )
            }

        /* This loop just builds any unbuilt modules. While building those modules, we might add additional module
         * instantiations. Those that haven't been encountered yet will be built on the next run.
         */
        do {
            val preLoopIncompleteModules = moduleInstantiationTracker.getUnbuiltModules()

            preLoopIncompleteModules.forEach {
                val module = buildModule(it)

                moduleInstantiationTracker.addBuiltModule(
                    instantiation = it.moduleInstantiationData,
                    module = module
                )
            }

            val postLoopIncompleteModules = moduleInstantiationTracker.getUnbuiltModules()
        } while (postLoopIncompleteModules.isNotEmpty())

        return moduleInstantiationTracker.getModules()
    }

    private fun buildModule(
        instantiation: ModuleInstantiationTracker.ModuleInstantiation,
    ): Module {
        val astNode = instantiation.astNode

        // Build input nodes
        val inputNodes = nodeBuilder.buildInputNodes(
            astNodes = astNode.inputFunctionIO,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
        )

        // Build output nodes
        val outputNodes = nodeBuilder.buildOutputNodes(
            astNodes = astNode.outputFunctionIO,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
        )

        // Build body nodes
        val nodeBuildResult = nodeBuilder.buildBodyNodes(
            astStatements = astNode.statements,
            inputNodes = inputNodes,
            outputNodes = outputNodes,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
        )

        /* Add the modules that were visited to the module instantiation tracker. If any of those modules haven't been
         * built yet, those modules will be built on the next loop of the buildAllModules function.
         */
        nodeBuildResult.moduleInstantiations.forEach {
            moduleInstantiationTracker.visitModule(it)
        }

        // Use the nodes from the node builder to create a module object
        val module = Module(
            moduleInvocation = ModuleInvocation(
                gaplFunctionName = instantiation.moduleInstantiationData.functionIdentifier,
            ),
            inputStructure = instantiation.input,
            outputStructure = instantiation.output,
            inputNodes = inputNodes,
            outputNodes = outputNodes,
            nodes = nodeBuildResult.nodes,
        )

        return module
    }

}