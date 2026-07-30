package com.uabutler.simgen

import com.uabutler.netlistir.netlist.Module

data class Port(val name: String, val shape: PortShape)

/** Extracts each top-level port's PortShape from a Module — every InputNode/OutputNode has exactly
 *  one WireVectorGroup, built directly from the port's own InterfaceStructure. */
object PortInspector {
    fun inputPorts(module: Module): List<Port> =
        module.getInputNodes().map { Port(it.name(), PortShape.fromInterfaceStructure(it.outputWireVectorGroups.single().gaplStructure)) }

    fun outputPorts(module: Module): List<Port> =
        module.getOutputNodes().map { Port(it.name(), PortShape.fromInterfaceStructure(it.inputWireVectorGroups.single().gaplStructure)) }
}
