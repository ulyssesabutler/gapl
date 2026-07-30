package com.uabutler.simengine

/**
 * A generic recursive value tree for a port's data, mirroring InterfaceStructure's own shape
 * (Wire/Vector/Record) but carrying values instead of type information. Independent of any
 * generated class - simengine cannot depend on simgen's generated wrapper code (simgen depends on
 * simengine, never the reverse), so this is the runtime marshaling boundary between the two:
 * generated wrapper classes convert to/from this, Engine reads/writes it directly against the
 * netlist.
 */
sealed interface PortValue {
    data class Bits(val bits: List<Boolean>) : PortValue
    data class Fields(val fields: Map<String, PortValue>) : PortValue
    data class Elements(val elements: List<PortValue>) : PortValue
}
