package com.uabutler.simtrace.layout

import com.uabutler.netlistir.netlist.Module
import java.util.IdentityHashMap

/**
 * Owned per-VcdTracer instance, not global — mirrors simengine's PlanCache exactly, including the
 * identity-keyed rationale (one Module object per distinct invocation; identity avoids recursing
 * through Module.Invocation structural equality on every lookup).
 */
class ModuleTraceLayoutCache {
    private val cache = IdentityHashMap<Module, ModuleTraceLayout>()

    fun getOrBuild(module: Module): ModuleTraceLayout = cache.getOrPut(module) { ModuleTraceLayoutBuilder.build(module) }
}
