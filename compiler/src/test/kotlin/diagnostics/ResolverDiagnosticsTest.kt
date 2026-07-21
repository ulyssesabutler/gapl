package diagnostics

import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.util.Logger
import diagnostics.DiagnosticsTestUtil.compileExpectingDiagnostics
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolverDiagnosticsTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `accessor on a function instantiation reports a diagnostic`() {
        val gapl = """
            function top() i: wire => o: wire {
                i => register(wire)[0] => o;
            }
        """.trimIndent()

        val diagnostics = compileExpectingDiagnostics(gapl)

        assertEquals(1, diagnostics.size)
        assertIs<ResolverDiagnosticKind.UnexpectedAccessorOnCircuitExpression>(diagnostics.first().kind)
    }
}
