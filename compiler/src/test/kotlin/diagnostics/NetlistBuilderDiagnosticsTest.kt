package diagnostics

import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.util.Logger
import diagnostics.DiagnosticsTestUtil.compileExpectingDiagnostics
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

        val diagnostics = compileExpectingDiagnostics(gapl)

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

        val diagnostics = compileExpectingDiagnostics(gapl)

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

        val diagnostics = compileExpectingDiagnostics(gapl)

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

        val diagnostics = compileExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        val kind = assertIs<BuilderDiagnosticKind.ExpectedModuleInstantiation>(diagnostics.first().kind)
        assertEquals("n", kind.identifierText)
    }
}
