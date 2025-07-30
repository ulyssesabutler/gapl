package com.uabutler.netlistir.builder

import com.uabutler.ast.node.ProgramNode
import com.uabutler.netlistir.builder.util.ModuleInstantiationTracker
import com.uabutler.netlistir.builder.util.ProgramContext
import com.uabutler.netlistir.netlist.Module

class ModuleBuilder(val program: ProgramNode) {

    val programContext = ProgramContext(program)
    private val functionNodes = program.functions
        .associateBy { it.identifier.value }

    private val moduleInstantiationTracker = ModuleInstantiationTracker(
        programContext = programContext,
        functionNodes = functionNodes,
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
                    Module.Invocation(
                        gaplFunctionName = it.identifier.value,
                        interfaces = emptyList(),
                        parameters = emptyList(),
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
                    instantiation = it.moduleInvocation,
                    module = module
                )
            }

            val postLoopIncompleteModules = moduleInstantiationTracker.getUnbuiltModules()
        } while (postLoopIncompleteModules.isNotEmpty())

        return moduleInstantiationTracker.getModules()
    }

    private fun buildModule(
        instantiation: ModuleInstantiationTracker.ModuleInstantiation,
    ) = Module(
        invocation = instantiation.moduleInvocation,
    ).also { module ->
        println("BUILDING MODULE: ${module.invocation.gaplFunctionName}")
        println("  Using parameter values: ${instantiation.genericParameterValues}")

        NodeBuilder(
            programContext = programContext,
            moduleInstantiationTracker = moduleInstantiationTracker,
            module = module,
            functionDefinitionAstNode = instantiation.astNode,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
        ).buildNodesIntoModule()
    }

}