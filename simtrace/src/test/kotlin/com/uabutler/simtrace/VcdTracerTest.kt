package com.uabutler.simtrace

import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.simengine.Engine
import com.uabutler.vcd.VcdWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcdTracerTest {

    private fun compile(gapl: String): List<Module> {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        return result.modules!!
    }

    private fun buildEngine(modules: List<Module>, topName: String): Engine {
        val top = modules.first { it.invocation.gaplFunctionName == topName }
        return Engine.build(modules, top.invocation)
    }

    private fun signalId(text: String, name: String): String {
        val match = Regex("""\${'$'}var wire \d+ (\S+) ${Regex.escape(name)} \${'$'}end""").find(text)
            ?: error("signal '$name' not found in:\n$text")
        return match.groupValues[1]
    }

    private val nestedRegisterGapl = """
        function helper() i: wire[8] => o: wire[8] {
            i => register(wire[8]) => o;
        }
        function test() i1: wire[8], i2: wire[8] => o1: wire[8], o2: wire[8] {
            i1 => declare named_helper: helper() => o1;
            i2 => helper() => o2;
        }
    """.trimIndent()

    private fun bits(value: Int): List<Boolean> = (0 until 8).map { (value shr it) and 1 == 1 }

    @Test
    fun `nested scopes with independently declared sibling signals`() {
        val modules = compile(nestedRegisterGapl)
        val engine = buildEngine(modules, "test")
        val sink = StringBuilder()
        val tracer = VcdTracer(engine, VcdWriter(sink))
        tracer.dumpInitial()

        val text = sink.toString()
        assertTrue(text.contains("\$scope module named_helper \$end"))
        assertTrue(text.contains("\$scope module helper_0 \$end"))
        assertEquals(2, Regex("""\${'$'}upscope \${'$'}end""").findAll(text).count())

        // Same cached ModuleTraceLayout (helper's) declared independently for both call sites - two
        // $var lines for its register, with distinct VCD identifier codes.
        val ids = Regex("""\${'$'}var wire 8 (\S+) RegisterFunction_0 \${'$'}end""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `sibling register state diverges per instance and output aliases track it`() {
        val modules = compile(nestedRegisterGapl)
        val engine = buildEngine(modules, "test")
        val sink = StringBuilder()
        val tracer = VcdTracer(engine, VcdWriter(sink))
        tracer.dumpInitial()

        engine.writeInputPort("i1", bits(1))
        engine.writeInputPort("i2", bits(2))
        tracer.tick()

        assertEquals(1, engine.readOutputPort("o1").foldIndexed(0) { i, acc, b -> if (b) acc or (1 shl i) else acc })
        assertEquals(2, engine.readOutputPort("o2").foldIndexed(0) { i, acc, b -> if (b) acc or (1 shl i) else acc })

        val text = sink.toString()
        val o1Id = signalId(text, "o1")
        val o2Id = signalId(text, "o2")

        assertTrue(text.contains("b00000001 $o1Id\n"), "expected o1 to trace value 1 after tick")
        assertTrue(text.contains("b00000010 $o2Id\n"), "expected o2 to trace value 2 after tick")
    }

    @Test
    fun `golden text for a simple flat module`() {
        val modules = compile(
            """
            function test() i: wire[8] => o: wire[8] {
                i => register(wire[8]) => o;
            }
            """.trimIndent(),
        )
        val engine = buildEngine(modules, "test")
        val sink = StringBuilder()
        val tracer = VcdTracer(engine, VcdWriter(sink))
        tracer.dumpInitial()
        engine.writeInputPort("i", bits(5))
        tracer.tick()

        // Declaration order: InputNodes, then OutputNodes (alias), then body nodes.
        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 8 ! i \$end\n" +
                "\$var wire 8 \" o \$end\n" +
                "\$var wire 8 # RegisterFunction_0 \$end\n" +
                "\$enddefinitions \$end\n" +
                "\$dumpvars\n" +
                "b00000000 !\n" +
                "b00000000 \"\n" +
                "b00000000 #\n" +
                "\$end\n" +
                "#1\n" +
                "b00000101 !\n" +
                "b00000101 \"\n" +
                "b00000101 #\n",
            sink.toString(),
        )
    }
}
