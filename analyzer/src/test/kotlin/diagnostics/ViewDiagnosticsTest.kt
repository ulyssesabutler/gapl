package diagnostics

import com.uabutler.Analyzer
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.util.Logger
import diagnostics.AnalyzerDiagnosticsTestUtil.compileExpectingDiagnostics
import diagnostics.AnalyzerDiagnosticsTestUtil.compileFullExpectingDiagnostics
import diagnostics.AnalyzerDiagnosticsTestUtil.defaultOptions
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Views (type-system.md §3): definitions, the project/overlay/zeros conversions, and the checks the
 * builder runs when a view is used. Behaviour (which bits actually land where) is covered end to end
 * by sim-test's `views` fixture; these tests cover what is accepted and what is rejected.
 */
class ViewDiagnosticsTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    private val beat = """
        interface beat { data: wire[16]; keep: wire[2]; }
    """.trimIndent()

    private fun compilesCleanly(gapl: String, options: Analyzer.Options = defaultOptions) {
        val result = Analyzer.analyzeFull(gapl, options)
        assertEquals(emptyList(), result.diagnostics.map { it.message })
        assertNotNull(result.modules)
    }

    // ---- accepted ----

    @Test
    fun `project, overlay and zeros compile`() {
        compilesCleanly("""
            $beat
            view hdr of beat { lo: .data[0:3]; k: .keep[1]; }

            function test() i: beat, nib: wire[4] => h: hdr, o: beat, z: beat {
                i => project(beat, hdr) => h;

                declare edited: hdr;
                nib => edited.lo;
                i.keep[1] => edited.k;
                i, edited => overlay(beat, hdr) => o;

                zeros(beat) => z;
            }
        """.trimIndent())
    }

    @Test
    fun `a view passes through generic primitives like any interface`() {
        compilesCleanly("""
            $beat
            view hdr of beat { lo: .data[0:3]; }

            function test() i: beat => o: hdr {
                i => project(beat, hdr) => register(hdr) => o;
            }
        """.trimIndent())
    }

    @Test
    fun `a view of a vector base, a parameterized view, a view of a view and an alias of a view all compile`() {
        compilesCleanly("""
            interface word wire[8]
            interface block word[4]
            view head of block { first_low: [0][0:3]; rest: [1:3]; }

            interface sized(n: integer) { payload: wire[n]; }
            view prefix(n: integer, k: integer) of sized(n) { front: .payload[0:k - 1]; }

            view inner of head { top: .rest[2]; }
            interface head_alias head

            function test() b: block, s: sized(12) => h: head, p: prefix(12, 5), t: inner {
                b => project(block, head_alias) => declare hh: head;
                hh => h;
                s => project(sized(12), prefix(12, 5)) => p;
                hh => project(head, inner) => t;
            }
        """.trimIndent())
    }

    @Test
    fun `project can be passed to a higher-order function`() {
        compilesCleanly(
            """
                $beat
                view hdr of beat { lo: .data[0:3]; }

                function test() i: beat[3] => o: hdr[3] {
                    i => vector_map(beat, hdr, 3, project(beat, hdr)) => o;
                }
            """.trimIndent(),
            Analyzer.Options(includeStdLib = true),
        )
    }

    // ---- rejected ----

    @Test
    fun `overlapping fields are rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            view nibbles of beat { byte: .data[0:7]; low: .data[0:3]; }

            function test() i: beat => o: nibbles {
                i => project(beat, nibbles) => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.OverlappingViewFields>(diagnostics.single().kind)
        assertEquals("nibbles", kind.viewName)
        assertEquals("byte", kind.firstField)
        assertEquals("low", kind.secondField)
    }

    @Test
    fun `a path naming a missing member is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            view hdr of beat { lo: .bogus[0:3]; }

            function test() i: hdr => o: hdr {
                i => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.InvalidViewFieldPath>(diagnostics.single().kind)
        assertEquals("hdr", kind.viewName)
        assertEquals("lo", kind.fieldName)
    }

    @Test
    fun `an out-of-range slice is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            view hdr of beat { lo: .data[8:16]; }

            function test() i: hdr => o: hdr {
                i => o;
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.InvalidViewFieldPath>(diagnostics.single().kind)
    }

    @Test
    fun `an out-of-range index is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            view hdr of beat { k: .keep[2]; }

            function test() i: hdr => o: hdr {
                i => o;
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.InvalidViewFieldPath>(diagnostics.single().kind)
    }

    @Test
    fun `a slice that is not the last accessor is rejected by the resolver`() {
        val diagnostics = compileExpectingDiagnostics("""
            interface pair { a: wire[4]; b: wire[4]; }
            interface pairs pair[4]
            view firsts of pairs { a: [0:1].a; }
        """.trimIndent())

        assertIs<ResolverDiagnosticKind.UnsupportedSliceAccess>(diagnostics.single().kind)
    }

    @Test
    fun `project requires its second interface to be a view`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            interface other { lo: wire[4]; }

            function test() i: beat => o: other {
                i => project(beat, other) => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.InvalidViewConversion>(diagnostics.single().kind)
        assertEquals("project", kind.conversion)
    }

    @Test
    fun `overlay requires the view's base to match`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat
            interface wide { data: wire[32]; }
            view hdr of beat { lo: .data[0:3]; }

            function test() w: wide, h: hdr => o: wide {
                w, h => overlay(wide, hdr) => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.InvalidViewConversion>(diagnostics.single().kind)
        assertEquals("overlay", kind.conversion)
    }

    @Test
    fun `zeros requires exactly one interface`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $beat

            function test() null => o: beat {
                zeros(beat, beat) => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.InvalidViewConversion>(diagnostics.single().kind)
        assertEquals("zeros", kind.conversion)
    }
}
