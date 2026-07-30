package com.uabutler.simengine

import com.uabutler.Analyzer
import com.uabutler.simengine.testsupport.bits
import com.uabutler.simengine.testsupport.toIntValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compiles the same source as a couple of small simtest fixtures via [Analyzer.analyzeFull] (needs
 * only :analyzer, no :compiler) and drives the resulting [Engine] through a sequence comparable to
 * those fixtures' existing C++ Verilator harnesses (simtest/tests/simple-register/test.cpp,
 * simtest/tests/simple-passthrough/test.cpp), to catch any divergence between the interpreter's
 * semantics and the existing Verilog-simulation ground truth.
 */
class EndToEndTest {

    private fun buildEngine(gapl: String): Engine {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        val modules = result.modules!!
        val top = modules.first { it.invocation.gaplFunctionName == "test" }
        return Engine.build(modules, top.invocation)
    }

    // Mirrors simtest/tests/simple-passthrough/test.gapl
    @Test
    fun `passthrough reflects the current input with no delay`() {
        val engine = buildEngine("function test() i: wire[8] => o: wire[8] { i => o; }")

        val rng = java.util.Random(12345)
        repeat(10) {
            val value = rng.nextInt(256)
            engine.writeInputPort("i", bits(value, 8))
            engine.settle()
            assertEquals(value, engine.readOutputPort("o").toIntValue())

            engine.tick()
            assertEquals(value, engine.readOutputPort("o").toIntValue())
        }
    }

    // Mirrors simtest/tests/simple-register/test.gapl
    @Test
    fun `register delays the input by exactly one tick`() {
        val engine = buildEngine("function test() i: wire[8] => o: wire[8] { i => register(wire[8]) => o; }")

        // Initial register state is zero.
        assertEquals(0, engine.readOutputPort("o").toIntValue())

        val rng = java.util.Random(12345)
        var previous = 0
        repeat(10) {
            val value = rng.nextInt(256)
            engine.writeInputPort("i", bits(value, 8))

            engine.settle()
            assertEquals(previous, engine.readOutputPort("o").toIntValue(), "output should still reflect the prior tick before latching")

            engine.tick()
            assertEquals(value, engine.readOutputPort("o").toIntValue(), "output should reflect this tick's input immediately after latching")

            previous = value
        }
    }

    @Test
    fun `record-shaped port round-trips through PortValue`() {
        val engine = buildEngine(
            """
            interface pair_type {
                a: wire[8];
                b: wire[4];
            }
            function test() i: pair_type => o: pair_type { i => o; }
            """.trimIndent(),
        )

        val value = PortValue.Fields(mapOf("a" to PortValue.Bits(bits(200, 8)), "b" to PortValue.Bits(bits(9, 4))))
        engine.writeInputPort("i", value)
        engine.settle()

        assertEquals(value, engine.readOutputPortValue("o"))
    }

    @Test
    fun `array-of-records port keeps each element's fields independent, not scrambled across elements`() {
        val engine = buildEngine(
            """
            interface pair_type {
                a: wire[8];
                b: wire[4];
            }
            function test() i: pair_type[3] => o: pair_type[3] { i => o; }
            """.trimIndent(),
        )

        val elements = listOf(
            PortValue.Fields(mapOf("a" to PortValue.Bits(bits(1, 8)), "b" to PortValue.Bits(bits(2, 4)))),
            PortValue.Fields(mapOf("a" to PortValue.Bits(bits(3, 8)), "b" to PortValue.Bits(bits(4, 4)))),
            PortValue.Fields(mapOf("a" to PortValue.Bits(bits(5, 8)), "b" to PortValue.Bits(bits(6, 4)))),
        )
        engine.writeInputPort("i", PortValue.Elements(elements))
        engine.settle()

        assertEquals(PortValue.Elements(elements), engine.readOutputPortValue("o"))
    }
}
