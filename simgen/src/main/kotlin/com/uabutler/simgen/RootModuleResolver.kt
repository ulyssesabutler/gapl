package com.uabutler.simgen

import com.uabutler.netlistir.netlist.Module

/**
 * Resolves which root module a wrapper should be generated for. Takes the already-computed root
 * list rather than a full module list — computing that (via InvocationGraph) is WrapperGenerator's
 * job — so the zero/multiple-root branches are directly testable without needing a compiled fixture.
 */
object RootModuleResolver {
    fun resolve(rootModules: List<Module>, targetModuleName: String?): Module {
        if (targetModuleName != null) {
            return rootModules.find { it.invocation.gaplFunctionName == targetModuleName }
                ?: error(
                    "No root module named '$targetModuleName' found. Available root modules: " +
                        rootModules.map { it.invocation.gaplFunctionName }
                )
        }
        return when (rootModules.size) {
            0 -> error(
                "No root modules found in the compiled program. This shouldn't happen for a " +
                    "program that compiled cleanly — please report this as a bug."
            )
            1 -> rootModules.single()
            else -> error(
                "Multiple root modules found (${rootModules.map { it.invocation.gaplFunctionName }}) " +
                    "— pass an explicit target module name to WrapperGenerator.generate(...)."
            )
        }
    }
}
