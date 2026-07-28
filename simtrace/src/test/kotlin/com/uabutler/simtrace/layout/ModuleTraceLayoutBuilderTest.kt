package com.uabutler.simtrace.layout

import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModuleTraceLayoutBuilderTest {

    private fun compile(gapl: String): List<Module> {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        return result.modules!!
    }

    private fun moduleNamed(modules: List<Module>, gaplFunctionName: String): Module =
        modules.first { it.invocation.gaplFunctionName == gaplFunctionName }

    @Test
    fun `named nodes keep their name, anonymous ones get a per-nodeType-per-module counter`() {
        val modules = compile(
            """
            function test() a: wire[8], b: wire[8], c: wire[8] => sum_out: wire[8], sum2_out: wire[8], diff_out: wire[8] {
                a, b => declare named_sum: add(8);
                named_sum, c => add(8) => sum_out;
                a, c => add(8) => sum2_out;
                a, c => subtract(8) => diff_out;
            }
            """.trimIndent(),
        )
        val layout = ModuleTraceLayoutBuilder.build(moduleNamed(modules, "test"))

        val expectedNames = setOf(
            "a", "b", "c", "named_sum",
            "AdditionFunction_0", "AdditionFunction_1", "SubtractionFunction_0",
            "sum_out", "sum2_out", "diff_out",
        )
        assertEquals(expectedNames, layout.signals.map { it.localName }.toSet())
        assertTrue(layout.signals.all { it.width == 8 })
    }

    @Test
    fun `anonymous pass-through nodes are skipped, named ones kept`() {
        val modules = compile(
            """
            function test() i: wire[8] => o: wire[8] {
                i => wire[8] => declare named_pass: wire[8];
                named_pass => o;
            }
            """.trimIndent(),
        )
        val layout = ModuleTraceLayoutBuilder.build(moduleNamed(modules, "test"))

        assertEquals(setOf("i", "named_pass", "o"), layout.signals.map { it.localName }.toSet())
    }

    @Test
    fun `nested module calls produce correctly named child scopes`() {
        val modules = compile(
            """
            function helper() i: wire[8] => o: wire[8] {
                i => register(wire[8]) => o;
            }
            function test() i1: wire[8], i2: wire[8] => o1: wire[8], o2: wire[8] {
                i1 => declare named_helper: helper() => o1;
                i2 => helper() => o2;
            }
            """.trimIndent(),
        )
        val testModule = moduleNamed(modules, "test")
        val helperModule = moduleNamed(modules, "helper")

        val testLayout = ModuleTraceLayoutBuilder.build(testModule)
        assertEquals(setOf("named_helper", "helper_0"), testLayout.children.map { it.scopeName }.toSet())
        // Each invocation also surfaces as a signal in the caller's own scope (its own output value).
        assertTrue(testLayout.signals.map { it.localName }.containsAll(listOf("named_helper", "helper_0")))

        val helperLayout = ModuleTraceLayoutBuilder.build(helperModule)
        assertEquals(setOf("i", "RegisterFunction_0", "o"), helperLayout.signals.map { it.localName }.toSet())
    }

    @Test
    fun `layout cache reuses the same layout for the same module`() {
        val modules = compile(
            """
            function helper() i: wire[8] => o: wire[8] {
                i => register(wire[8]) => o;
            }
            function test() i1: wire[8], i2: wire[8] => o1: wire[8], o2: wire[8] {
                i1 => declare named_helper: helper() => o1;
                i2 => helper() => o2;
            }
            """.trimIndent(),
        )
        val helperModule = moduleNamed(modules, "helper")
        val testModule = moduleNamed(modules, "test")

        val cache = ModuleTraceLayoutCache()
        assertSame(cache.getOrBuild(helperModule), cache.getOrBuild(helperModule))
        assertNotSame(cache.getOrBuild(helperModule), cache.getOrBuild(testModule))
    }
}
