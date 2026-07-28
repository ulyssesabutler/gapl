package com.uabutler.simengine.plan

import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.util.LogicalNotFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.simengine.testsupport.inputPort
import com.uabutler.simengine.testsupport.outputPort
import com.uabutler.simengine.testsupport.predefinedFunctionNode
import com.uabutler.simengine.testsupport.testModule
import com.uabutler.simengine.testsupport.wire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlanBuilderTest {

    @Test
    fun `orders a simple combinational chain topologically`() {
        val module = testModule()
        val input = module.inputPort("a", 1)
        val not1 = module.predefinedFunctionNode("not1", LogicalNotFunction)
        val not2 = module.predefinedFunctionNode("not2", LogicalNotFunction)
        val output = module.outputPort("out", 1)

        module.wire(input.outputWires()[0], not1.inputWires()[0])
        module.wire(not1.outputWires()[0], not2.inputWires()[0])
        module.wire(not2.outputWires()[0], output.inputWires()[0])

        val plan = PlanBuilder.build(module)
        assertEquals(listOf(not1, not2), plan.evaluationOrder)
    }

    @Test
    fun `a register feeding its own combinational input does not throw`() {
        val module = testModule()
        val reg = module.predefinedFunctionNode("reg", RegisterFunction(storageStructure = WireInterfaceStructure))
        val not = module.predefinedFunctionNode("not", LogicalNotFunction)

        // feedback loop through a register: reg.current -> not.input -> not.output -> reg.next
        module.wire(reg.outputWires()[0], not.inputWires()[0])
        module.wire(not.outputWires()[0], reg.inputWires()[0])

        val plan = PlanBuilder.build(module)
        assertEquals(listOf(not), plan.evaluationOrder)
        assertEquals(listOf(reg), plan.registerNodes)
    }

    @Test
    fun `a genuine combinational cycle throws`() {
        val module = testModule()
        val not1 = module.predefinedFunctionNode("not1", LogicalNotFunction)
        val not2 = module.predefinedFunctionNode("not2", LogicalNotFunction)

        module.wire(not1.outputWires()[0], not2.inputWires()[0])
        module.wire(not2.outputWires()[0], not1.inputWires()[0])

        assertFailsWith<IllegalArgumentException> { PlanBuilder.build(module) }
    }

    @Test
    fun `index maps cover every wire in the module`() {
        val module = testModule()
        val input = module.inputPort("a", 1)
        val not1 = module.predefinedFunctionNode("not1", LogicalNotFunction)
        val output = module.outputPort("out", 1)

        module.wire(input.outputWires()[0], not1.inputWires()[0])
        module.wire(not1.outputWires()[0], output.inputWires()[0])

        val plan = PlanBuilder.build(module)

        val allOutputWires = module.getNodes().flatMap { it.outputWires() }
        val allInputWires = module.getNodes().flatMap { it.inputWires() }
        assertEquals(allOutputWires.size, plan.outputWireCount)
        assertEquals(allOutputWires.toSet(), plan.outputWireIndex.keys)
        assertEquals(allInputWires.toSet(), plan.inputWireSource.keys)

        // not1's input resolves to the same storage slot as the input port's output.
        assertEquals(
            plan.outputWireIndex.getValue(input.outputWires()[0]),
            plan.inputWireSource.getValue(not1.inputWires()[0]),
        )
    }
}
