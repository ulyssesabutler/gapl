package com.uabutler.netlistir.builder

import com.uabutler.ast.node.ProgramNode
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.netlistir.builder.util.BuilderDiagnosticException
import com.uabutler.netlistir.builder.util.ModuleInstantiationTracker
import com.uabutler.netlistir.builder.util.ProgramContext
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule

class ModuleBuilder(val program: ProgramNode) {

    val programContext = ProgramContext(program)
    private val functionNodes = program.functions
        .associateBy { it.identifier.value }

    private val moduleInstantiationTracker = ModuleInstantiationTracker(
        programContext = programContext,
        functionNodes = functionNodes,
    )

    private val diagnosticsCollector = DiagnosticsCollector()

    data class Result(
        val modules: List<MutableModule>,
        val diagnostics: List<Diagnostic>,
    )

    fun buildAllModules(): Result {
        /* First, add all concrete modules to the set of modules. These are modules that don't have any
         * generic parameters. These modules can be built without any additional information or context. To build
         * any other module, we need to know the actual parameter values.
         */
        program.functions
            .filter { it.genericInterfaces.isEmpty() && it.genericParameters.isEmpty() }
            .forEach {
                try {
                    moduleInstantiationTracker.visitModule(
                        Module.Invocation(
                            gaplFunctionName = it.identifier.value,
                            interfaces = emptyList(),
                            parameters = emptyList(),
                        ),
                        it.span,
                    )
                } catch (e: BuilderDiagnosticException) {
                    diagnosticsCollector.report(e.diagnostic)
                }
            }

        /* This loop just builds any unbuilt modules. While building those modules, we might add additional module
         * instantiations. Those that haven't been encountered yet will be built on the next run.
         */
        do {
            val preLoopIncompleteModules = moduleInstantiationTracker.getUnbuiltModules()

            preLoopIncompleteModules.forEach {
                try {
                    val module = buildModule(it)

                    moduleInstantiationTracker.addBuiltModule(
                        instantiation = it.moduleInvocation,
                        module = module
                    )
                } catch (e: BuilderDiagnosticException) {
                    diagnosticsCollector.report(e.diagnostic)
                    moduleInstantiationTracker.markFailed(it.moduleInvocation)
                }
            }

            val postLoopIncompleteModules = moduleInstantiationTracker.getUnbuiltModules()
        } while (postLoopIncompleteModules.isNotEmpty())

        return Result(
            modules = moduleInstantiationTracker.getModules(),
            diagnostics = diagnosticsCollector.diagnostics(),
        )
    }

    private fun buildModule(
        instantiation: ModuleInstantiationTracker.ModuleInstantiation,
    ) = MutableModule(
        invocation = instantiation.moduleInvocation,
    ).also { module ->
        NodeBuilder(
            programContext = programContext,
            moduleInstantiationTracker = moduleInstantiationTracker,
            module = module,
            functionDefinitionAstNode = instantiation.astNode,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
            diagnosticsCollector = diagnosticsCollector,
        ).buildNodesIntoModule()
    }

}
