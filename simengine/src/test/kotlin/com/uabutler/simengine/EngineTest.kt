package com.uabutler.simengine

import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.simengine.testsupport.bits
import com.uabutler.simengine.testsupport.inputPort
import com.uabutler.simengine.testsupport.moduleInvocationNode
import com.uabutler.simengine.testsupport.outputPort
import com.uabutler.simengine.testsupport.predefinedFunctionNode
import com.uabutler.simengine.testsupport.testModule
import com.uabutler.simengine.testsupport.toIntValue
import com.uabutler.simengine.testsupport.wire
import com.uabutler.simengine.testsupport.wireAll
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTest {

    @Test
    fun `settle computes combinational output from inputs`() {
        val module = testModule()
        val a = module.inputPort("a", 4)
        val b = module.inputPort("b", 4)
        val add = module.predefinedFunctionNode("add", AdditionFunction(size = 4))
        val sum = module.outputPort("sum", 4)

        val lhs = add.inputWireVectorGroups.first { it.identifier == "lhs" }.wires()
        val rhs = add.inputWireVectorGroups.first { it.identifier == "rhs" }.wires()
        module.wireAll(a.outputWires(), lhs)
        module.wireAll(b.outputWires(), rhs)
        module.wireAll(add.outputWires(), sum.inputWires())

        val engine = Engine.build(listOf(module), module.invocation)
        engine.writeInputPort("a", bits(3, 4))
        engine.writeInputPort("b", bits(4, 4))
        engine.settle()

        assertEquals(7, engine.readOutputPort("sum").toIntValue())
    }

    @Test
    fun `register output only updates on tick, not settle`() {
        val module = testModule()
        val d = module.inputPort("d", 1)
        val reg = module.predefinedFunctionNode("reg", RegisterFunction(storageStructure = WireInterfaceStructure))
        val q = module.outputPort("q", 1)

        module.wire(d.outputWires()[0], reg.inputWires()[0])
        module.wire(reg.outputWires()[0], q.inputWires()[0])

        val engine = Engine.build(listOf(module), module.invocation)
        engine.writeInputPort("d", listOf(true))

        engine.settle()
        assertEquals(listOf(false), engine.readOutputPort("q"))

        engine.tick()
        assertEquals(listOf(true), engine.readOutputPort("q"))
    }

    @Test
    fun `sibling invocations of the same module have independent register state`() {
        val child = MutableModule(Module.Invocation("child", emptyList(), emptyList()))
        val d = child.inputPort("d", 1)
        val reg = child.predefinedFunctionNode("reg", RegisterFunction(storageStructure = WireInterfaceStructure))
        val q = child.outputPort("q", 1)
        child.wire(d.outputWires()[0], reg.inputWires()[0])
        child.wire(reg.outputWires()[0], q.inputWires()[0])

        val parent = testModule()
        val d1 = parent.inputPort("d1", 1)
        val d2 = parent.inputPort("d2", 1)
        val q1 = parent.outputPort("q1", 1)
        val q2 = parent.outputPort("q2", 1)
        val call1 = parent.moduleInvocationNode("call1", child)
        val call2 = parent.moduleInvocationNode("call2", child)

        parent.wire(d1.outputWires()[0], call1.inputWires()[0])
        parent.wire(call1.outputWires()[0], q1.inputWires()[0])
        parent.wire(d2.outputWires()[0], call2.inputWires()[0])
        parent.wire(call2.outputWires()[0], q2.inputWires()[0])

        val engine = Engine.build(listOf(parent, child), parent.invocation)

        engine.writeInputPort("d1", listOf(true))
        engine.writeInputPort("d2", listOf(true))
        engine.tick()
        assertEquals(listOf(true), engine.readOutputPort("q1"))
        assertEquals(listOf(true), engine.readOutputPort("q2"))

        // Flip only call1's input; call2's latched state must stay independent, not shared.
        engine.writeInputPort("d1", listOf(false))
        engine.tick()
        assertEquals(listOf(false), engine.readOutputPort("q1"))
        assertEquals(listOf(true), engine.readOutputPort("q2"))
    }
}
