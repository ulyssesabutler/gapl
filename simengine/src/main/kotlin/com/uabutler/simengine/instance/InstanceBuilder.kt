package com.uabutler.simengine.instance

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.simengine.plan.PlanCache

/** Recursively builds a [ModuleInstance] tree, resolving each [ModuleInvocationNode]'s invocation
 *  key to its actual [Module] via [moduleResolver], sharing one [PlanCache] across the recursion. */
class InstanceBuilder(
    private val moduleResolver: (Module.Invocation) -> Module,
    private val planCache: PlanCache,
) {
    fun build(module: Module): ModuleInstance {
        val plan = planCache.getOrBuild(module)
        val children = module.getBodyNodes()
            .filterIsInstance<ModuleInvocationNode>()
            .associate { it.name() to build(moduleResolver(it.invocation)) }
        return ModuleInstance(module, plan, children)
    }
}
