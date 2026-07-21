package diagnostics

import com.uabutler.util.Logger
import diagnostics.DiagnosticsTestUtil.compileExpectingDiagnostics
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(
            "Unable to match generic parameter values for double: expected 1 generic parameter(s), got 0",
            diagnostics.first().message,
        )
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
        assertEquals(
            "Unable to match generic parameter values for double: expected 1 generic parameter(s), got 2",
            diagnostics.first().message,
        )
    }
}
