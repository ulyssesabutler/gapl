package com.uabutler.simengine

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

    fun writeInputPort(portName: String, values: List<Boolean>) {
        val node = top.module.getInputNode(portName)
        node.outputWires().zip(values).forEach { (w, v) -> top.write(w, v) }
    }

    fun readOutputPort(portName: String): List<Boolean> =
        top.module.getOutputNode(portName).inputWires().map(top::read)
}
