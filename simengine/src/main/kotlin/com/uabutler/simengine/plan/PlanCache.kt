package com.uabutler.simengine.plan

import com.uabutler.netlistir.netlist.Module
import java.util.IdentityHashMap

/**
 * Owned per-[com.uabutler.simengine.Engine] instance, not global — avoids cross-test/cross-program
 * leakage. Identity-keyed (not [Module.Invocation] structural equality): the netlist builder produces
 * exactly one [Module] object per distinct invocation, so identity and structural equality coincide
 * here, and identity avoids recursing through a potentially-nested [Module.Invocation] on every lookup.
 */
class PlanCache {
    private val cache = IdentityHashMap<Module, ModulePlan>()

    fun getOrBuild(module: Module): ModulePlan = cache.getOrPut(module) { PlanBuilder.build(module) }
}
