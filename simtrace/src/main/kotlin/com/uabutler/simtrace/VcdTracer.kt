package com.uabutler.simtrace

import com.uabutler.netlistir.netlist.Wire
import com.uabutler.simengine.Engine
import com.uabutler.simengine.instance.ModuleInstance
import com.uabutler.simtrace.layout.ModuleTraceLayoutCache
import com.uabutler.vcd.SignalId
import com.uabutler.vcd.VcdWriter

/**
 * Bridges a headless simengine Engine to a real VCD file via vcd's VcdWriter: recursively walks the
 * Engine's instance tree once at construction, declaring one VCD signal per traced WireVector (see
 * ModuleTraceLayoutBuilder for the naming/tracing policy) with a $scope per nested ModuleInstance.
 * One VCD time unit per Engine.tick() call — no sub-cycle tracing (simengine's IR has no explicit
 * clock to key finer granularity on).
 *
 * Usage: construct, call dumpInitial() once (captures pre-any-tick state as VCD time 0), then call
 * tick() repeatedly (each call drives engine.tick(), advances VCD time by 1, and records every
 * traced signal's new value — VcdWriter's own internal dedup means this is safe to do
 * unconditionally without simtrace doing its own diffing).
 */
class VcdTracer(private val engine: Engine, private val writer: VcdWriter) {
    private class DeclaredRef(val instance: ModuleInstance, val wires: List<Wire>)

    private val layoutCache = ModuleTraceLayoutCache()
    private val declared: List<Pair<SignalId, DeclaredRef>>
    private var time = 0L
    private var initialDumped = false

    init {
        val acc = mutableListOf<Pair<SignalId, DeclaredRef>>()
        declareRecursive(engine.top, emptyList(), acc)
        declared = acc
    }

    private fun declareRecursive(instance: ModuleInstance, scope: List<String>, acc: MutableList<Pair<SignalId, DeclaredRef>>) {
        val layout = layoutCache.getOrBuild(instance.module)
        for (signal in layout.signals) {
            acc += writer.declareSignal(scope, signal.localName, signal.width) to DeclaredRef(instance, signal.wires)
        }
        for (child in layout.children) {
            // node.name() is the RAW node identifier (possibly "anonymous_NN") — the actual key into
            // ModuleInstance.children, set by InstanceBuilder's `associate { it.name() to ... }`.
            // child.scopeName is the (possibly synthesized) display name and is NOT usable as the key.
            val childInstance = instance.children.getValue(child.node.name())
            declareRecursive(childInstance, scope + child.scopeName, acc)
        }
    }

    private fun currentValues(): Map<SignalId, List<Boolean>> =
        declared.associate { (id, ref) -> id to ref.wires.map { ref.instance.read(it) } }

    fun dumpInitial() {
        check(!initialDumped) { "dumpInitial() has already been called" }
        initialDumped = true
        writer.writeHeader()
        writer.dumpInitialValues(currentValues())
    }

    fun tick() {
        check(initialDumped) { "dumpInitial() must be called before tick()" }
        engine.tick()
        time += 1
        writer.advanceTime(time)
        currentValues().forEach { (id, values) -> writer.writeValue(id, values) }
    }
}
