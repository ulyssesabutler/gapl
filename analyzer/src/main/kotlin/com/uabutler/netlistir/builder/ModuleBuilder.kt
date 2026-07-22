package com.uabutler.netlistir.builder

import com.uabutler.ast.node.ProgramNode
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.netlistir.builder.util.BuilderDiagnosticException
import com.uabutler.netlistir.builder.util.ModuleInstantiationTracker
import com.uabutler.netlistir.builder.util.ProgramContext
import com.uabutler.netlistir.builder.util.findCombinationalLoops
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node

class ModuleBuilder(
    val program: ProgramNode,
    // Used only to prefer a combinational-loop diagnostic's anchor span in the user's own source
    // over one inside the invisible prepended stdlib text - see validateNoCombinationalLoops.
    // Defaults to 0 ("nothing is stdlib") so existing callers/tests that don't care don't need to
    // pass it.
    private val stdLibLineOffset: Int = 0,
) {

    val programContext = ProgramContext(program)
    private val functionNodes = program.functions
        .associateBy { it.identifier.value }

    private val moduleInstantiationTracker = ModuleInstantiationTracker(
        programContext = programContext,
        functionNodes = functionNodes,
    )

    private val diagnosticsCollector = DiagnosticsCollector()

    // Spans are recorded per-function inside NodeBuilder, but the combinational-loop check runs
    // across every built module at once (a loop can span a function call), after every individual
    // NodeBuilder has already finished - so spans are pulled out and merged here as each module
    // finishes building, rather than looked up from any single NodeBuilder instance.
    private val nodeSpans = mutableMapOf<Node, SourceSpan>()

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

        val modules = moduleInstantiationTracker.getModules()

        // The loop check builds a whole-program graph via the same converter compiler's retiming
        // pass uses, which resolves wire connections eagerly (throws on an unconnected input wire,
        // and NPEs on a ModuleInvocationNode referencing a module that failed to build). Any earlier
        // diagnostic already means the netlist may be structurally incomplete - skip rather than
        // risk exactly those crashes analyzing something already known to be broken.
        if (diagnosticsCollector.diagnostics().isEmpty()) {
            validateNoCombinationalLoops(modules)
        }

        return Result(
            modules = modules,
            diagnostics = diagnosticsCollector.diagnostics(),
        )
    }

    private fun buildModule(
        instantiation: ModuleInstantiationTracker.ModuleInstantiation,
    ) = MutableModule(
        invocation = instantiation.moduleInvocation,
    ).also { module ->
        val nodeBuilder = NodeBuilder(
            programContext = programContext,
            moduleInstantiationTracker = moduleInstantiationTracker,
            module = module,
            functionDefinitionAstNode = instantiation.astNode,
            interfaceValuesContext = instantiation.genericInterfaceValues,
            parameterValuesContext = instantiation.genericParameterValues,
            diagnosticsCollector = diagnosticsCollector,
        )
        nodeBuilder.buildNodesIntoModule()
        nodeSpans.putAll(nodeBuilder.nodeSpans)
    }

    private fun validateNoCombinationalLoops(modules: List<MutableModule>) {
        findCombinationalLoops(modules).forEach { loop ->
            // loop is already ranked most-to-least relevant (see findCombinationalLoops); the only
            // thing it can't judge for itself is visibility - a node inside the prepended stdlib
            // text is invisible/unclickable to the user, so among equally-ranked candidates prefer
            // whichever one the user can actually see in their own file.
            val anchorNode = loop.firstOrNull { nodeSpans.getValue(it).startLine > stdLibLineOffset } ?: loop.first()
            val span = nodeSpans.getValue(anchorNode)

            val involvedNodes = loop.map {
                BuilderDiagnosticKind.CombinationalLoop.NodeInLoop(
                    nodeName = it.name(),
                    functionName = it.parentModule.invocation.gaplFunctionName,
                )
            }.distinct() // the same (name, function) pair can repeat many times across call sites of one generic helper

            diagnosticsCollector.reportError(BuilderDiagnosticKind.CombinationalLoop(involvedNodes), span)
        }
    }

}
