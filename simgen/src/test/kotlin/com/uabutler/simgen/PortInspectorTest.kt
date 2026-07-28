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
        assertEquals(listOf(FlatPort("i", 8)), PortInspector.inputPorts(module))
        assertEquals(listOf(FlatPort("o", 8)), PortInspector.outputPorts(module))
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
        assertEquals(listOf(FlatPort("i1", 8), FlatPort("i2", 4)), PortInspector.inputPorts(module))
        assertEquals(listOf(FlatPort("o1", 8), FlatPort("o2", 4)), PortInspector.outputPorts(module))
    }
}
