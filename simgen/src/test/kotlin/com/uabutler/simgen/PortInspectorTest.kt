package com.uabutler.simgen

import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortInspectorTest {

    private fun compile(gapl: String): Module {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        return result.modules!!.first { it.invocation.gaplFunctionName == "test" }
    }

    @Test
    fun `single input and output port`() {
        val module = compile(
            """
            function test() i: wire[8] => o: wire[8] {
                i => o;
            }
            """.trimIndent(),
        )
        assertEquals(listOf(Port("i", PortShape.Leaf(8))), PortInspector.inputPorts(module))
        assertEquals(listOf(Port("o", PortShape.Leaf(8))), PortInspector.outputPorts(module))
    }

    @Test
    fun `multiple ports preserve declaration order`() {
        val module = compile(
            """
            function test() i1: wire[8], i2: wire[4] => o1: wire[8], o2: wire[4] {
                i1 => o1;
                i2 => o2;
            }
            """.trimIndent(),
        )
        assertEquals(listOf(Port("i1", PortShape.Leaf(8)), Port("i2", PortShape.Leaf(4))), PortInspector.inputPorts(module))
        assertEquals(listOf(Port("o1", PortShape.Leaf(8)), Port("o2", PortShape.Leaf(4))), PortInspector.outputPorts(module))
    }

    @Test
    fun `record-shaped port`() {
        val module = compile(
            """
            interface pair_type {
                a: wire[8];
                b: wire[4];
            }

            function test() i: pair_type => o: wire[8] {
                i.a => o;
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(Port("i", PortShape.Record(mapOf("a" to PortShape.Leaf(8), "b" to PortShape.Leaf(4))))),
            PortInspector.inputPorts(module),
        )
    }

    @Test
    fun `array of records is a genuine Vector shape, not collapsed`() {
        val module = compile(
            """
            interface pair_type {
                a: wire[8];
                b: wire[4];
            }

            function test() i: pair_type[3] => o: wire[8] {
                i[0].a => o;
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Port(
                    "i",
                    PortShape.Vector(PortShape.Record(mapOf("a" to PortShape.Leaf(8), "b" to PortShape.Leaf(4))), 3),
                )
            ),
            PortInspector.inputPorts(module),
        )
    }

    @Test
    fun `array of plain wires collapses to a single flat Leaf`() {
        val module = compile(
            """
            function test() i: wire[8][3] => o: wire[8] {
                i[0] => o;
            }
            """.trimIndent(),
        )
        assertEquals(listOf(Port("i", PortShape.Leaf(24))), PortInspector.inputPorts(module))
    }
}
