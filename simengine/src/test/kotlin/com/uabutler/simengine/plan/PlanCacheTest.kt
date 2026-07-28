package com.uabutler.simengine.plan

import com.uabutler.netlistir.util.LogicalNotFunction
import com.uabutler.simengine.testsupport.inputPort
import com.uabutler.simengine.testsupport.outputPort
import com.uabutler.simengine.testsupport.predefinedFunctionNode
import com.uabutler.simengine.testsupport.testModule
import com.uabutler.simengine.testsupport.wire
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PlanCacheTest {

    private fun simpleModule(): com.uabutler.netlistir.netlist.MutableModule {
        val module = testModule()
        val input = module.inputPort("a", 1)
        val not = module.predefinedFunctionNode("not", LogicalNotFunction)
        val output = module.outputPort("out", 1)
        module.wire(input.outputWires()[0], not.inputWires()[0])
        module.wire(not.outputWires()[0], output.inputWires()[0])
        return module
    }

    @Test
    fun `returns the same plan instance for the same module`() {
        val module = simpleModule()
        val cache = PlanCache()
        assertSame(cache.getOrBuild(module), cache.getOrBuild(module))
    }

    @Test
    fun `returns distinct plans for distinct modules`() {
        val cache = PlanCache()
        val planA = cache.getOrBuild(simpleModule())
        val planB = cache.getOrBuild(simpleModule())
        assertNotSame(planA, planB)
    }
}
