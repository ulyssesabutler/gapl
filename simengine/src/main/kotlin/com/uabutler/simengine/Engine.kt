package com.uabutler.simengine

import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.RecordInterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.flatWidth
import com.uabutler.netlistir.netlist.Module
import com.uabutler.simengine.instance.InstanceBuilder
import com.uabutler.simengine.instance.ModuleInstance
import com.uabutler.simengine.plan.PlanCache

/**
 * Headless simulation engine: interprets a program's untransformed netlist IR directly, without
 * going through Verilog. `settle()` runs one combinational settle pass; `tick()` additionally
 * latches register state. No auto-settle-on-read/write — callers write inputs, call `settle()`
 * explicitly, then read outputs, keeping the model fully explicit and imperative.
 */
class Engine private constructor(
    /** Root of the instance tree — public so external walkers (e.g. a VCD tracer) can recurse
     *  through [ModuleInstance.children] and read arbitrary wire values via [ModuleInstance.read]. */
    val top: ModuleInstance,
) {
    companion object {
        fun build(modules: List<Module>, topInvocation: Module.Invocation): Engine {
            val byInvocation = modules.associateBy { it.invocation }
            val topModule = byInvocation[topInvocation]
                ?: error("Top-level invocation $topInvocation not found among the ${modules.size} supplied modules")

            val planCache = PlanCache()
            val resolver: (Module.Invocation) -> Module = { inv ->
                byInvocation[inv] ?: error("Unknown invocation $inv — missing from the modules list passed to Engine.build")
            }

            return Engine(InstanceBuilder(resolver, planCache).build(topModule))
        }
    }

    fun settle() = top.settle()

    fun tick() {
        top.settle()
        top.latchRegisters()
        // A ModuleInvocationNode's output is a *copy*, propagated into the parent's own wire
        // storage during settle() — unlike a flat module's output ports, which resolve live through
        // to whatever wire actually drives them. That copy is only as fresh as the last settle()
        // pass, so a register that just latched inside a child module needs one more settle() to
        // repropagate its new value up through any enclosing invocation before it's externally visible.
        top.settle()
    }

    fun writeInputPort(portName: String, values: List<Boolean>) = writeInputPort(portName, PortValue.Bits(values))

    fun writeInputPort(portName: String, value: PortValue) {
        val group = top.module.getInputNode(portName).outputWireVectorGroups.single()
        writeValue(group.gaplStructure, emptyList(), emptyList(), value) { fieldPath, indices, bits ->
            val vector = group.wireVectors.single { it.identifier == fieldPath }
            val wires = vector.projection(fieldPath, indices, null)
                ?: error("No wires for port '$portName' field $fieldPath at indices $indices")
            wires.wires.zip(bits).forEach { (w, v) -> top.write(w, v) }
        }
    }

    fun readOutputPort(portName: String): List<Boolean> = (readOutputPortValue(portName) as PortValue.Bits).bits

    fun readOutputPortValue(portName: String): PortValue {
        val group = top.module.getOutputNode(portName).inputWireVectorGroups.single()
        return readValue(group.gaplStructure, emptyList(), emptyList()) { fieldPath, indices ->
            val vector = group.wireVectors.single { it.identifier == fieldPath }
            val wires = vector.projection(fieldPath, indices, null)
                ?: error("No wires for port '$portName' field $fieldPath at indices $indices")
            wires.wires.map(top::read)
        }
    }

    private fun writeValue(
        structure: InterfaceStructure,
        fieldPath: List<String>,
        indices: List<Int>,
        value: PortValue,
        writeLeaf: (fieldPath: List<String>, indices: List<Int>, bits: List<Boolean>) -> Unit,
    ) {
        if (structure.flatWidth() != null) {
            writeLeaf(fieldPath, indices, (value as PortValue.Bits).bits)
            return
        }
        when (structure) {
            is RecordInterfaceStructure -> {
                val fields = (value as PortValue.Fields).fields
                structure.ports.forEach { (k, sub) -> writeValue(sub, fieldPath + k, indices, fields.getValue(k), writeLeaf) }
            }
            is VectorInterfaceStructure -> {
                val elements = (value as PortValue.Elements).elements
                elements.forEachIndexed { idx, elementValue ->
                    writeValue(structure.vectoredInterface, fieldPath, indices + idx, elementValue, writeLeaf)
                }
            }
            else -> error("unreachable: flatWidth() is only null for RecordInterfaceStructure or a Vector wrapping one")
        }
    }

    private fun readValue(
        structure: InterfaceStructure,
        fieldPath: List<String>,
        indices: List<Int>,
        readLeaf: (fieldPath: List<String>, indices: List<Int>) -> List<Boolean>,
    ): PortValue {
        if (structure.flatWidth() != null) {
            return PortValue.Bits(readLeaf(fieldPath, indices))
        }
        return when (structure) {
            is RecordInterfaceStructure -> PortValue.Fields(
                structure.ports.mapValues { (k, sub) -> readValue(sub, fieldPath + k, indices, readLeaf) }
            )
            is VectorInterfaceStructure -> PortValue.Elements(
                (0 until structure.size).map { idx -> readValue(structure.vectoredInterface, fieldPath, indices + idx, readLeaf) }
            )
            else -> error("unreachable: flatWidth() is only null for RecordInterfaceStructure or a Vector wrapping one")
        }
    }
}
