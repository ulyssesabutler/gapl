package com.uabutler.simgen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WrapperGeneratorTest {

    @Test
    fun `generates expected class name, properties, and baked-in descriptors`() {
        val file = WrapperGenerator.generate(
            """
            function test() i: wire[8] => o: wire[8] {
                i => o;
            }
            """.trimIndent(),
        )
        val text = file.toString()

        assertTrue(text.contains("class TestSimulator"))
        assertTrue(text.contains("public var i: List<Boolean> = List(8) { false }"))
        assertTrue(text.contains("public val o: List<Boolean>"))
        assertTrue(text.contains("gaplFunctionName = \"test\""))
        assertTrue(text.contains("expectedInputs = listOf(PortDescriptor(\"i\", 8))"))
        assertTrue(text.contains("expectedOutputs = listOf(PortDescriptor(\"o\", 8))"))
        assertTrue(text.contains("engine.writeInputPort(\"i\", value)"))
        assertTrue(text.contains("engine.readOutputPort(\"o\")"))
        assertTrue(text.contains("engine.settle()"))
        assertTrue(text.contains("engine.tick()"))
    }

    @Test
    fun `default class name is derived from the resolved function name`() {
        val file = WrapperGenerator.generate(
            """
            function my_design() i: wire[4] => o: wire[4] {
                i => o;
            }
            """.trimIndent(),
        )
        assertEquals("MyDesignSimulator", file.name)
    }

    @Test
    fun `explicit targetModuleName selects the right module among multiple roots`() {
        val file = WrapperGenerator.generate(
            """
            function a() i: wire[4] => o: wire[4] {
                i => o;
            }
            function b() i: wire[2] => o: wire[2] {
                i => o;
            }
            """.trimIndent(),
            targetModuleName = "b",
        )
        assertEquals("BSimulator", file.name)
        assertTrue(file.toString().contains("public var i: List<Boolean> = List(2) { false }"))
    }

    @Test
    fun `default includeStdLib does not confuse root selection with unused stdlib helpers`() {
        // Regression check: WrapperGenerator's internal Analyzer.analyzeFull call defaults to
        // includeStdLib = true, and several stdlib helpers aren't called by this design at all -
        // they'd otherwise also count as "root modules" (no incoming invocation edges) and break
        // the single-root auto-selection.
        val file = WrapperGenerator.generate(
            """
            function test() i: wire[8] => o: wire[8] {
                i => o;
            }
            """.trimIndent(),
        )
        assertEquals("TestSimulator", file.name)
    }

    @Test
    fun `port named after a Kotlin keyword is backtick escaped in the generated code`() {
        // "object" isn't a GAPL keyword, so this is plain valid GAPL - but it IS a Kotlin keyword,
        // so KotlinPoet must backtick-escape it as a Kotlin property name.
        val file = WrapperGenerator.generate(
            """
            function test() object: wire[4] => o: wire[4] {
                object => o;
            }
            """.trimIndent(),
        )
        assertTrue(file.toString().contains("`object`"))
    }

    private val simpleFixture = """
        function test() i: wire[8] => o: wire[8] {
            i => o;
        }
    """.trimIndent()

    @Test
    fun `generated class implements AutoCloseable and accepts an optional vcdOutput parameter`() {
        val text = WrapperGenerator.generate(simpleFixture).toString()

        assertTrue(text.contains(": AutoCloseable"))
        assertTrue(text.contains("vcdOutput: File? = null"))
        assertTrue(text.contains("private val tracer: VcdTracer?"))
        assertTrue(text.contains("private val vcdWriterSink: Writer?"))
        assertTrue(text.contains("import java.io.File"))
    }

    @Test
    fun `tick delegates to the tracer when active and to the engine directly otherwise`() {
        val text = WrapperGenerator.generate(simpleFixture).toString()

        assertTrue(text.contains("val activeTracer = tracer"))
        assertTrue(text.contains("if (activeTracer != null)"))
        assertTrue(text.contains("activeTracer.tick()"))
    }

    @Test
    fun `close is overridden and closes the underlying vcd writer sink`() {
        val text = WrapperGenerator.generate(simpleFixture).toString()

        assertTrue(text.contains("override fun close()"))
        assertTrue(text.contains("vcdWriterSink?.close()"))
    }
}
