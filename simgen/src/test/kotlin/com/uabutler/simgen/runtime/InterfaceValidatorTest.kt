package com.uabutler.simgen.runtime

import com.uabutler.Analyzer
import com.uabutler.Analyzer.FullAnalysisResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InterfaceValidatorTest {

    private fun compile(gapl: String): FullAnalysisResult =
        Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))

    private val originalSource = """
        function test() i: wire[8] => o: wire[8] {
            i => o;
        }
    """.trimIndent()

    private val expectedInputs = listOf(PortDescriptor("i", 8))
    private val expectedOutputs = listOf(PortDescriptor("o", 8))

    @Test
    fun `matching interface returns the module`() {
        val analysis = compile(originalSource)
        val module = InterfaceValidator.validate("test", analysis.modules, analysis.diagnostics, expectedInputs, expectedOutputs)
        assertEquals("test", module.invocation.gaplFunctionName)
    }

    @Test
    fun `resized port throws mentioning expected and actual`() {
        val changed = compile(
            """
            function test() i: wire[4] => o: wire[4] {
                i => o;
            }
            """.trimIndent(),
        )
        val exception = assertFailsWith<IllegalStateException> {
            InterfaceValidator.validate("test", changed.modules, changed.diagnostics, expectedInputs, expectedOutputs)
        }
        assertTrue(exception.message!!.contains("changed"))
        assertTrue(exception.message!!.contains("PortDescriptor(name=i, width=8)")) // expected
        assertTrue(exception.message!!.contains("PortDescriptor(name=i, width=4)")) // actual
    }

    @Test
    fun `target function missing throws a distinct message`() {
        val changed = compile(
            """
            function renamed() i: wire[8] => o: wire[8] {
                i => o;
            }
            """.trimIndent(),
        )
        val exception = assertFailsWith<IllegalStateException> {
            InterfaceValidator.validate("test", changed.modules, changed.diagnostics, expectedInputs, expectedOutputs)
        }
        assertTrue(exception.message!!.contains("no longer exists"))
    }

    @Test
    fun `failed compilation throws a distinct message with diagnostics`() {
        // Syntactically valid but semantically broken (undefined reference), so this hits the
        // resolver-diagnostic path (modules == null) rather than throwing DiagnosticsException
        // directly from the parser, which a genuine syntax error would (see Analyzer.analyze's
        // guarded { ... } parser wrapping).
        val broken = compile(
            """
            function test() i: wire[8] => o: wire[8] {
                undefined_thing => o;
            }
            """.trimIndent(),
        )
        assertTrue(broken.diagnostics.isNotEmpty(), "expected the broken fixture to actually fail to compile")

        val exception = assertFailsWith<IllegalStateException> {
            InterfaceValidator.validate("test", broken.modules, broken.diagnostics, expectedInputs, expectedOutputs)
        }
        assertTrue(exception.message!!.contains("Failed to compile"))
    }
}
