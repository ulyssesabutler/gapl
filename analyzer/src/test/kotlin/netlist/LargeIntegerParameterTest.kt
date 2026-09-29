package netlist

import com.uabutler.Analyzer
import com.uabutler.util.Logger
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertTrue

class LargeIntegerParameterTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    // Regression: PredefinedFunction.search used to convert the first integer parameter of every
    // invocation to an Int up front, so a user function whose first argument exceeded Int.MAX_VALUE
    // threw ArithmeticException from the netlist builder, before the name was even checked.
    @Test
    fun `user function with a first integer parameter wider than 32 bits builds`() {
        val gapl = """
            function wide_constant(high: integer, low: integer) null => o: wire[64] {
                literal(32, high) => o[32:63];
                literal(32, low)  => o[0:31];
            }

            function top() x: wire[64] => y: wire[64] {
                wide_constant(2147483648, 4294967295) => y;
            }
        """.trimIndent()

        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))

        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `64-bit literal with the top bit set builds`() {
        val gapl = """
            function top() x: wire[64] => y: wire[64] {
                literal(64, 9223372036854775808) => y;
            }
        """.trimIndent()

        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))

        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
    }
}
