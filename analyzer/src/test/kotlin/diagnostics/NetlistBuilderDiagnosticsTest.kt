package diagnostics

import com.uabutler.Analyzer
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.util.Logger
import diagnostics.AnalyzerDiagnosticsTestUtil.compileFullExpectingDiagnostics
import diagnostics.AnalyzerDiagnosticsTestUtil.defaultOptions
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetlistBuilderDiagnosticsTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `generic function invoked with too few generic parameters reports a diagnostic`() {
        val gapl = """
            function double(size: integer) i: wire[size] => o: wire[size] {
                i => o;
            }

            function top() x: wire[4] => y: wire[4] {
                x => double() => y;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.GenericParameterArityMismatch>(diagnostics.first().kind)
        assertEquals("double", kind.functionName)
        assertEquals(1, kind.expected)
        assertEquals(0, kind.actual)
    }

    @Test
    fun `generic function invoked with too many generic parameters reports a diagnostic`() {
        val gapl = """
            function double(size: integer) i: wire[size] => o: wire[size] {
                i => o;
            }

            function top() x: wire[4] => y: wire[4] {
                x => double(4, 5) => y;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.GenericParameterArityMismatch>(diagnostics.first().kind)
        assertEquals("double", kind.functionName)
        assertEquals(1, kind.expected)
        assertEquals(2, kind.actual)
    }

    @Test
    fun `function-typed generic parameter used as a vector bound reports a diagnostic`() {
        val gapl = """
            function bad(op: wire => wire) i: wire[op] => o: wire {
                i[0] => o;
            }

            function top() i: wire => o: wire {
                i => bad(register(wire)) => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.StaticExpressionParameterNotInteger>(diagnostics.first().kind)
        assertEquals("op", kind.identifierText)
    }

    @Test
    fun `integer-typed generic parameter used directly as a circuit node reports a diagnostic`() {
        val gapl = """
            function bad(n: integer) i: wire => o: wire {
                i => n => o;
            }

            function top() i: wire => o: wire {
                i => bad(5) => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.ExpectedModuleInstantiation>(diagnostics.first().kind)
        assertEquals("n", kind.identifierText)
    }

    @Test
    fun `undriven output port reports a diagnostic`() {
        val gapl = """
            function test() i: wire => o: wire {
                i => declare x: wire;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.UndrivenOutputPort>(diagnostics.first().kind)
        assertEquals("o", kind.portName)
        assertEquals("test", kind.functionName)
    }

    @Test
    fun `undriven body node input reports a diagnostic`() {
        val gapl = """
            function test() i: wire => o: wire {
                declare reg1: register(wire) => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.UndrivenNodeInput>(diagnostics.first().kind)
        assertEquals("reg1", kind.nodeName)
        assertEquals("test", kind.functionName)
    }

    @Test
    fun `undriven wires in two independent functions are both reported in one compile`() {
        val gapl = """
            function first() i: wire => o: wire {
                i => declare x: wire;
            }

            function second() i: wire => o: wire {
                declare reg1: register(wire) => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(2, diagnostics.size)
        val outputKind = assertIs<BuilderDiagnosticKind.UndrivenOutputPort>(diagnostics[0].kind)
        assertEquals("first", outputKind.functionName)
        val inputKind = assertIs<BuilderDiagnosticKind.UndrivenNodeInput>(diagnostics[1].kind)
        assertEquals("second", inputKind.functionName)
    }

    @Test
    fun `an output port driven by two connections reports a diagnostic instead of crashing`() {
        val gapl = """
            function test() i: wire, i2: wire => o: wire {
                i => o;
                i2 => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.MultiplyDrivenOutputPort>(diagnostics.first().kind)
        assertEquals("o", kind.portName)
        assertEquals("test", kind.functionName)
    }

    @Test
    fun `a multi-bit output port driven twice reports one diagnostic, not one per bit`() {
        val gapl = """
            function test() i: wire[2], i2: wire[2] => o: wire[2] {
                i => o;
                i2 => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.MultiplyDrivenOutputPort>(diagnostics.first().kind)
        assertEquals("o", kind.portName)
    }

    @Test
    fun `the same connection repeated three times reports two diagnostics, one for each redundant connection`() {
        val gapl = """
            function test() i: wire => o: wire {
                i => o;
                i => o;
                i => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(2, diagnostics.size)
        diagnostics.forEach { assertIs<BuilderDiagnosticKind.MultiplyDrivenOutputPort>(it.kind) }
    }

    @Test
    fun `a body node input driven by two connections reports a diagnostic`() {
        val gapl = """
            function test() i: wire, i2: wire => o: wire {
                i => declare reg1: register(wire);
                i2 => reg1;
                reg1 => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.MultiplyDrivenNodeInput>(diagnostics.first().kind)
        assertEquals("reg1", kind.nodeName)
        assertEquals("test", kind.functionName)
    }

    @Test
    fun `a function's own output port used as a source reports a diagnostic instead of crashing`() {
        val gapl = """
            function test() i: wire[2] => o: wire[2] {
                o => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.WireCountMismatch>(diagnostics.first().kind)
        assertEquals("test", kind.gaplModuleName)
    }

    @Test
    fun `a function's own input port used as a target reports a diagnostic instead of crashing`() {
        val gapl = """
            function test() i: wire[2] => o: wire[2] {
                i => i;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.WireCountMismatch>(diagnostics.first().kind)
        assertEquals("test", kind.gaplModuleName)
    }

    @Test
    fun `multiply-driven wires in two independent functions are both reported in one compile`() {
        val gapl = """
            function first() i: wire, i2: wire => o: wire {
                i => o;
                i2 => o;
            }

            function second() i: wire, i2: wire => o: wire {
                i => declare reg1: register(wire);
                i2 => reg1;
                reg1 => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(2, diagnostics.size)
        val outputKind = assertIs<BuilderDiagnosticKind.MultiplyDrivenOutputPort>(diagnostics[0].kind)
        assertEquals("first", outputKind.functionName)
        val inputKind = assertIs<BuilderDiagnosticKind.MultiplyDrivenNodeInput>(diagnostics[1].kind)
        assertEquals("second", inputKind.functionName)
    }

    @Test
    fun `a node combinationally driving its own input reports a diagnostic`() {
        val gapl = """
            function test() i: wire[2] => o: wire[2] {
                declare x: wire[2] => x;
                i => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.first().kind)
        assertEquals(listOf("x"), kind.involvedNodes.map { it.nodeName })
        assertEquals("test", kind.involvedNodes.single().functionName)
    }

    @Test
    fun `two nodes combinationally driving each other reports one diagnostic naming both`() {
        val gapl = """
            function test() i: wire[2] => o: wire[2] {
                declare x: wire[2] => declare y: wire[2] => x;
                i => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.first().kind)
        assertEquals(listOf("x", "y"), kind.involvedNodes.map { it.nodeName })
    }

    @Test
    fun `a register in the loop is not reported as a combinational loop`() {
        val gapl = """
            function test() i: wire[2] => o: wire[2] {
                declare reg1: register(wire[2]) => declare x: wire[2] => reg1;
                reg1 => o;
            }
        """.trimIndent()

        val result = Analyzer.analyzeFull(gapl, defaultOptions)

        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `a combinational loop spanning a function call is reported, naming nodes in both functions`() {
        val gapl = """
            function passthrough() a: wire[2] => b: wire[2] {
                a => b;
            }

            function top() i: wire[2] => o: wire[2] {
                declare x: wire[2] => passthrough() => x;
                i => o;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.first().kind)
        assertEquals(
            setOf("passthrough" to "a", "passthrough" to "b", "top" to "x"),
            kind.involvedNodes.map { it.functionName to it.nodeName }.toSet(),
        )
    }

    @Test
    fun `a combinational loop anchors on the user's declared node, not a stdlib helper's generic i-o parameter`() {
        val gapl = """
            function test() a: wire => b: wire {
                declare x: wire => wire_to_vector() => vector_to_wire() => x;
                a => b;
            }
        """.trimIndent()

        // Real standard library included this time, specifically to exercise the "prefer a node
        // in the user's own source over one inside the invisible prepended stdlib text" ranking -
        // wire_to_vector/vector_to_wire are real stdlib helpers with generic i/o parameter names.
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = true))

        assertEquals(1, result.diagnostics.size)
        val diagnostic = result.diagnostics.first()
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostic.kind)

        assertEquals(
            setOf("x" to "test", "i" to "wire_to_vector", "o" to "wire_to_vector", "i" to "vector_to_wire", "o" to "vector_to_wire"),
            kind.involvedNodes.map { it.nodeName to it.functionName }.toSet(),
        )

        // The chosen span points at the user's own declared node, not a stdlib helper's parameter -
        // and consequently isn't flagged as falling inside the invisible prepended stdlib text.
        assertEquals(2, diagnostic.span.startLine) // `declare x: wire => ...` is line 2 of the test source
        assertEquals(null, diagnostic.note)
    }

    @Test
    fun `a generic helper called twice in one loop is listed once, not once per call site`() {
        val gapl = """
            function helper(size: integer) i: wire[size] => o: wire[size] {
                i => o;
            }

            function test() a: wire[2] => b: wire[2] {
                declare x: wire[2] => helper(2) => helper(2) => x;
                a => b;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.first().kind)

        // helper(2) is called twice, so without dedup "i (in helper)"/"o (in helper)" would each
        // appear twice (once per call site's inlined copy) even though they're indistinguishable
        // in the message, which only shows the bare function name, not the generic instantiation.
        assertEquals(
            setOf("x" to "test", "i" to "helper", "o" to "helper"),
            kind.involvedNodes.map { it.nodeName to it.functionName }.toSet(),
        )
        assertEquals(3, kind.involvedNodes.size)
    }

    @Test
    fun `the same textual loop reported by multiple unrelated generic instantiations collapses to one diagnostic`() {
        // Mirrors aes/test.gapl's round_key(1..10): a generic function with a genuine textual bug,
        // called several times with a generic parameter that doesn't affect the bug at all - every
        // instantiation independently rediscovers the identical loop at the identical declaration
        // span, and should collapse to one diagnostic instead of one per instantiation.
        val gapl = """
            function buggy(unrelated: integer) i: wire[2] => o: wire[2] {
                declare x: wire[2] => x;
                i => o;
            }

            function callSite1() a: wire[2] => b: wire[2] {
                a => buggy(1) => b;
            }

            function callSite2() a: wire[2] => b: wire[2] {
                a => buggy(2) => b;
            }

            function callSite3() a: wire[2] => b: wire[2] {
                a => buggy(3) => b;
            }
        """.trimIndent()

        val diagnostics = compileFullExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.first().kind)
        assertEquals(listOf("x"), kind.involvedNodes.map { it.nodeName })
    }

    @Test
    fun `a register inside a called function correctly breaks a cross-module loop`() {
        val gapl = """
            function delay() a: wire[2] => b: wire[2] {
                a => declare reg1: register(wire[2]) => b;
            }

            function top() i: wire[2] => o: wire[2] {
                declare x: wire[2] => delay() => x;
                i => o;
            }
        """.trimIndent()

        val result = Analyzer.analyzeFull(gapl, defaultOptions)

        assertTrue(result.diagnostics.isEmpty())
    }
}
