package com.uabutler.interpreter

import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.simengine.Engine
import com.uabutler.simgen.PortInspector
import com.uabutler.simgen.PortShape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Compiles a small register-based design (mirroring EndToEndTest.kt's own no-files style) and drives
 * it through CycleRunner directly, covering the decided cycle-object semantics: checked right after
 * settle(), before that cycle's tick(); an omitted output is "don't care"; an omitted input holds its
 * previous value across ticks, not just within the same cycle; an unknown port name is a hard error.
 */
class CycleRunnerEndToEndTest {

    private fun compile(gapl: String): Module {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        return result.modules!!.first { it.invocation.gaplFunctionName == "test" }
    }

    private val registerGapl = "function test() i: wire[8] => o: wire[8] { i => register(wire[8]) => o; }"

    private fun run(module: Module, cyclesJson: String): List<CycleResult> {
        val engine = Engine.build(listOf(module), module.invocation)
        val inputShapes = PortInspector.inputPorts(module).associate { it.name to it.shape }
        val outputShapes = PortInspector.outputPorts(module).associate { it.name to it.shape }
        val cycles = Json.parseToJsonElement(cyclesJson) as JsonArray
        return CycleRunner.run(engine, inputShapes, outputShapes, cycles)
    }

    @Test
    fun `a correctly-predicted register delay passes every cycle`() {
        val module = compile(registerGapl)
        val results = run(
            module,
            """
            [
              { "i": "0x07", "o": "0x00" },
              { "i": "0x0A", "o": "0x07" },
              { "i": "0x0A", "o": "0x0A" }
            ]
            """.trimIndent(),
        )
        assertTrue(results.all { it.passed }, "expected every cycle to pass: $results")
    }

    @Test
    fun `a wrong expectation is reported as a mismatch, not silently ignored`() {
        val module = compile(registerGapl)
        val results = run(
            module,
            """
            [
              { "i": "0x07", "o": "0xFF" }
            ]
            """.trimIndent(),
        )
        assertEquals(1, results.size)
        assertTrue(!results[0].passed)
        assertEquals("o", results[0].mismatches.single().portName)
        assertEquals(PortValueJson.toDisplayString(PortValueJson.decode(PortShape.Leaf(8), Json.parseToJsonElement("\"0xFF\""), "o")), PortValueJson.toDisplayString(results[0].mismatches.single().expected))
    }

    @Test
    fun `an omitted output is a don't-care, not checked`() {
        val module = compile(registerGapl)
        // No "o" key at all - whatever the actual value is, this must still pass.
        val results = run(module, """[ { "i": "0x07" } ]""")
        assertTrue(results.single().passed)
    }

    @Test
    fun `an omitted input holds its previous value across a full tick, not just within the same cycle`() {
        val module = compile(registerGapl)
        val results = run(
            module,
            """
            [
              { "i": "0x07", "o": "0x00" },
              { "o": "0x07" },
              { "o": "0x07" }
            ]
            """.trimIndent(),
        )
        // Cycle 2's expected value (0x07, not 0x00) only holds if cycle 1's omitted "i" genuinely kept
        // driving 0x07 into cycle 1's own tick() - if omission had instead reset the input to 0, cycle
        // 2 would observe 0x00 here and this assertion would catch it.
        assertTrue(results.all { it.passed }, "expected every cycle to pass: $results")
    }

    @Test
    fun `an unknown port name is a hard error`() {
        val module = compile(registerGapl)
        assertFailsWith<IllegalStateException> {
            run(module, """[ { "i": "0x07", "typo_output": "0x00" } ]""")
        }
    }
}
