package com.uabutler.simengine.instance

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.netlist.Wire
import com.uabutler.netlistir.util.IntegerRegisterFunction
import com.uabutler.simengine.eval.PredefinedFunctionEvaluator
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simengine.plan.ModulePlan

/**
 * Mutable runtime state for one instance of a [Module]: wire values plus the hierarchical tree of
 * submodule instances. A [Module] can be invoked from multiple call sites (or the same call site
 * across sibling module invocations) — each gets its own [ModuleInstance] and therefore independent
 * register state, even when they share the same [plan].
 *
 * Wire storage is OutputWire-only, with InputWire reads resolved lazily via [ModulePlan.inputWireSource]
 * — an InputWire always has exactly one driver by netlist construction, so it never needs its own
 * storage slot.
 */
class ModuleInstance(
    val module: Module,
    val plan: ModulePlan,
    val children: Map<String, ModuleInstance>,
) {
    private val wireValues = BooleanArray(plan.outputWireCount)

    init {
        // IntegerRegisterFunction needs explicit default-value init; RegisterFunction's all-zero
        // reset is free (wireValues already defaults every slot to false).
        plan.registerNodes.forEach { node ->
            val fn = node.predefinedFunction
            if (fn is IntegerRegisterFunction) {
                node.outputWires().zip(unsignedBigIntegerToBits(fn.default, fn.size))
                    .forEach { (w, v) -> write(w, v) }
            }
        }
    }

    fun read(wire: Wire): Boolean = when (wire) {
        is OutputWire -> wireValues[plan.outputWireIndex.getValue(wire)]
        is InputWire -> wireValues[plan.inputWireSource.getValue(wire)]
    }

    fun write(wire: OutputWire, value: Boolean) {
        wireValues[plan.outputWireIndex.getValue(wire)] = value
    }

    /** One combinational settle pass: evaluate every non-register node in topological order. */
    fun settle() {
        for (node in plan.evaluationOrder) {
            when (node) {
                is PredefinedFunctionNode -> {
                    val outputs = PredefinedFunctionEvaluator.evaluate(node) { read(it) }
                    node.outputWires().zip(outputs).forEach { (w, v) -> write(w, v) }
                }

                is PassThroughNode ->
                    node.inputWires().zip(node.outputWires()).forEach { (i, o) -> write(o, read(i)) }

                is ModuleInvocationNode -> {
                    val child = children.getValue(node.name())

                    node.inputWireVectorGroups.forEach { group ->
                        val childInput = child.module.getInputNode(group.identifier)
                        group.wires().zip(childInput.outputWires())
                            .forEach { (parentIn, childOut) -> child.write(childOut, read(parentIn)) }
                    }

                    child.settle()

                    node.outputWireVectorGroups.forEach { group ->
                        val childOutput = child.module.getOutputNode(group.identifier)
                        group.wires().zip(childOutput.inputWires())
                            .forEach { (parentOut, childIn) -> write(parentOut, child.read(childIn)) }
                    }
                }

                else -> {}
            }
        }
    }

    /**
     * Commit every register's latched `next` -> `current`. Snapshots every register's `next` value
     * before writing anything, so a register reading another register's `current` never observes a
     * value that was only just latched this same tick.
     */
    fun latchRegisters() {
        val pending = plan.registerNodes.map { node -> node.inputWires().map { read(it) } }
        children.values.forEach { it.latchRegisters() }
        plan.registerNodes.zip(pending).forEach { (node, values) ->
            node.outputWires().zip(values).forEach { (w, v) -> write(w, v) }
        }
    }
}
