package com.uabutler.simgen

import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.OutputWireVectorGroup

data class FlatPort(val name: String, val width: Int)

/** Extracts flat wire[N] top-level port shapes from a Module. Records/vectors aren't supported —
 *  only flat wire[N] top-level ports are a design goal for simgen. */
object PortInspector {
    fun inputPorts(module: Module): List<FlatPort> =
        module.getInputNodes().map { flattenOutputGroups(it.name(), it.outputWireVectorGroups) }

    fun outputPorts(module: Module): List<FlatPort> =
        module.getOutputNodes().map { flattenInputGroups(it.name(), it.inputWireVectorGroups) }

    // Named distinctly (not overloaded) despite the near-identical bodies: List<InputWireVectorGroup>
    // and List<OutputWireVectorGroup> erase to the same JVM signature, so overloading on them alone
    // is a platform declaration clash at the bytecode level even though Kotlin's own type checker
    // treats them as distinct.
    private fun flattenOutputGroups(name: String, groups: List<OutputWireVectorGroup>): FlatPort {
        val group = groups.singleOrNull()
            ?: error("Port '$name' is not a flat wire[N] port — only flat wire[N] top-level ports are supported by simgen")
        val vector = group.wireVectors.singleOrNull()
            ?: error("Port '$name' is not a flat wire[N] port — only flat wire[N] top-level ports are supported by simgen")
        return FlatPort(name, vector.wires.size)
    }

    private fun flattenInputGroups(name: String, groups: List<InputWireVectorGroup>): FlatPort {
        val group = groups.singleOrNull()
            ?: error("Port '$name' is not a flat wire[N] port — only flat wire[N] top-level ports are supported by simgen")
        val vector = group.wireVectors.singleOrNull()
            ?: error("Port '$name' is not a flat wire[N] port — only flat wire[N] top-level ports are supported by simgen")
        return FlatPort(name, vector.wires.size)
    }
}
