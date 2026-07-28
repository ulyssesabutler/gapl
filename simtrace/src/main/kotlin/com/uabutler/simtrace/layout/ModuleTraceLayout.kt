package com.uabutler.simtrace.layout

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Wire

/**
 * One VCD signal to declare within a single Module's own scope: an unqualified local name (the
 * tracer prepends the accumulated scope path when declaring) and the wires carrying its value
 * (bit 0 = LSB, matching every WireVector's own order — VcdWriter handles the LSB->MSB reversal
 * needed for VCD's own b<...> format internally).
 */
class TracedSignal(val localName: String, val wires: List<Wire>) {
    val width: Int get() = wires.size
}

/**
 * One child scope to recurse into: the scope-path segment name (per the naming policy) paired with
 * the owning ModuleInvocationNode — node.name() (the raw identifier, possibly "anonymous_NN") is the
 * required lookup key into ModuleInstance.children, NOT scopeName.
 */
class ChildScope(val scopeName: String, val node: ModuleInvocationNode)

/**
 * Static, per-Module trace layout: which signals to declare at this module's own scope level, and
 * which child scopes to recurse into. Computed once per distinct Module object and cached via
 * ModuleTraceLayoutCache — mirrors simengine's ModulePlan/PlanCache exactly. Depends only on static
 * Module structure (node names, types, wire-vector shapes), never runtime values, so it's safe to
 * share across every ModuleInstance backed by the same Module (sibling call sites, recursive calls).
 */
class ModuleTraceLayout(
    val module: Module,
    val signals: List<TracedSignal>,
    val children: List<ChildScope>,
)
