package com.uabutler.simgen.runtime

import com.uabutler.diagnostics.Diagnostic
import com.uabutler.netlistir.netlist.Module
import com.uabutler.simgen.PortInspector

/**
 * Called from a generated wrapper's constructor. A wrapper is generated once against a snapshot of
 * a design's interface, but always recompiles the *current* GAPL source fresh at construction time —
 * so between generation and use, the source can have changed in an interface-breaking way without the
 * wrapper being regenerated. This checks the current, freshly-compiled interface against the
 * descriptor baked in at generation time, and fails loudly (rather than exposing wrong-shaped
 * properties or crashing confusingly inside Engine) if they've diverged.
 */
object InterfaceValidator {
    fun validate(
        gaplFunctionName: String,
        modules: List<Module>?,
        diagnostics: List<Diagnostic>,
        expectedInputs: List<PortDescriptor>,
        expectedOutputs: List<PortDescriptor>,
    ): Module {
        if (modules == null) {
            error("Failed to compile GAPL source:\n" + diagnostics.joinToString("\n"))
        }
        val target = modules.firstOrNull { it.invocation.gaplFunctionName == gaplFunctionName }
            ?: error("Function '$gaplFunctionName' no longer exists in the current source — regenerate this wrapper.")

        val actualInputs = PortInspector.inputPorts(target).map { PortDescriptor(it.name, it.width) }
        val actualOutputs = PortInspector.outputPorts(target).map { PortDescriptor(it.name, it.width) }

        if (actualInputs != expectedInputs || actualOutputs != expectedOutputs) {
            error(
                "Interface for '$gaplFunctionName' has changed since this wrapper was generated — regenerate it.\n" +
                    "  expected inputs:  $expectedInputs\n" +
                    "  actual inputs:    $actualInputs\n" +
                    "  expected outputs: $expectedOutputs\n" +
                    "  actual outputs:   $actualOutputs"
            )
        }
        return target
    }
}
