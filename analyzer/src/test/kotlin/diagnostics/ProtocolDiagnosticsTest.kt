package diagnostics

import com.uabutler.Analyzer
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.util.Logger
import com.uabutler.util.Protocol
import diagnostics.AnalyzerDiagnosticsTestUtil.compileExpectingDiagnostics
import diagnostics.AnalyzerDiagnosticsTestUtil.compileFullExpectingDiagnostics
import diagnostics.AnalyzerDiagnosticsTestUtil.defaultOptions
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Stage 2a of layer 3 (type-system.md §5): protocol annotations and the static checks on them. Nothing
 * here changes generated hardware yet - these tests cover what is accepted and what is rejected.
 */
class ProtocolDiagnosticsTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    private fun compilesCleanly(gapl: String, options: Analyzer.Options = defaultOptions) {
        val result = Analyzer.analyzeFull(gapl, options)
        assertEquals(emptyList(), result.diagnostics.map { it.message })
        assertNotNull(result.modules)
    }

    // A combinational @pure helper with one input, and one with two.
    private val helpers = """
        function inc() i: wire[8] => o: wire[8] {
            i, literal(8, 1) => add(8) => o;
        }

        function sum() a: wire[8], b: wire[8] => o: wire[8] {
            a, b => add(8) => o;
        }
    """.trimIndent()

    // ---- accepted ----

    @Test
    fun `annotated functions and islands compile`() {
        compilesCleanly("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8] {
                i => inc()@valid => o;
            }

            @ready_valid function rv() a: wire[8], b: wire[8] => o: wire[8] {
                a, b => sum()@ready_valid => declare s: wire[8];
                s => inc()@ready_valid => o;
            }
        """.trimIndent())
    }

    @Test
    fun `a @valid function can be an island in @ready_valid`() {
        compilesCleanly("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8] {
                i => inc()@valid => o;
            }

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => v()@ready_valid => o;
            }
        """.trimIndent())
    }

    @Test
    fun `an unannotated function is @pure and calls @pure functions without annotation`() {
        compilesCleanly("""
            $helpers

            function d() i: wire[8] => o: wire[8] {
                i => inc() => o;
            }
        """.trimIndent())
    }

    @Test
    fun `@valid forks implicitly`() {
        compilesCleanly("""
            $helpers

            @valid function v() i: wire[8] => a: wire[8], b: wire[8] {
                i => inc()@valid => a;
                i => inc()@valid => b;
            }
        """.trimIndent())
    }

    @Test
    fun `@ready_valid forks through fork_eager and fork_lazy`() {
        compilesCleanly("""
            $helpers

            @ready_valid function rv() i: wire[8] => a: wire[8], b: wire[8], c: wire[8], d: wire[8] {
                i => fork_eager(wire[8], 2) => declare x: wire[8], declare y: wire[8];
                x => inc()@ready_valid => a;
                y => fork_lazy(wire[8], 3) => b, declare z: wire[8], d;
                z => inc()@ready_valid => c;
            }
        """.trimIndent())
    }

    @Test
    fun `a multi-output island is allowed`() {
        compilesCleanly("""
            function split() i: wire[8] => lo: wire[4], hi: wire[4] {
                i[0:3] => lo;
                i[4:7] => hi;
            }

            @ready_valid function rv() i: wire[8] => lo: wire[4], hi: wire[4] {
                i => split()@ready_valid => lo, hi;
            }
        """.trimIndent())
    }

    // ---- resolver ----

    @Test
    fun `an unknown protocol is rejected`() {
        val diagnostics = compileExpectingDiagnostics("""
            @streaming function f() i: wire => o: wire {
                i => o;
            }
        """.trimIndent())

        val kind = assertIs<ResolverDiagnosticKind.UnknownProtocol>(diagnostics.single().kind)
        assertEquals("streaming", kind.name)
    }

    @Test
    fun `an annotation on something other than a call is rejected`() {
        val diagnostics = compileExpectingDiagnostics("""
            @valid function f() i: wire => o: wire {
                i@valid => o;
            }
        """.trimIndent())

        assertIs<ResolverDiagnosticKind.ProtocolAnnotationOnNonCall>(diagnostics.single().kind)
    }

    // ---- the coercion matrix ----

    @Test
    fun `a callee above its caller is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8] {
                i => inc()@valid => o;
            }

            function d() i: wire[8] => o: wire[8] {
                i => v() => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.IllegalProtocolCall>(diagnostics.single().kind)
        assertEquals(Protocol.PURE, kind.functionProtocol)
        assertEquals(Protocol.VALID, kind.calleeProtocol)
    }

    @Test
    fun `an unmarked island is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8] {
                i => inc() => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.MissingCoercion>(diagnostics.single().kind)
        assertEquals("inc", kind.calleeName)
    }

    @Test
    fun `an island marked with a protocol other than the caller's is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => inc()@valid => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.CoercionProtocolMismatch>(diagnostics.single().kind)
        assertEquals(Protocol.VALID, kind.written)
    }

    @Test
    fun `marking a call that is not an island is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            function d() i: wire[8] => o: wire[8] {
                i => inc()@pure => o;
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.UnnecessaryCoercion>(diagnostics.single().kind)
    }

    // ---- @valid ----

    @Test
    fun `a @valid function with two input ports is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            @valid function v() a: wire, b: wire => o: wire {
                a => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.ValidFunctionHasMultipleInputs>(diagnostics.single().kind)
        assertEquals(2, kind.inputCount)
    }

    @Test
    fun `a join inside a @valid body is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8] {
                i => inc()@valid => declare x: wire[8];
                i, x => sum()@valid => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.JoinInValidFunction>(diagnostics.single().kind)
        assertEquals("sum", kind.calleeName)
    }

    @Test
    fun `a @valid function gets a valid port per data port`() {
        val result = Analyzer.analyzeFull("""
            $helpers

            @valid function v() i: wire[8] => o: wire[8], p: wire[8] {
                i => inc()@valid => o;
                i => inc()@valid => p;
            }
        """.trimIndent(), defaultOptions)

        assertEquals(emptyList(), result.diagnostics.map { it.message })
        val module = result.modules!!.single { it.invocation.gaplFunctionName == "v" }
        assertEquals(listOf("i", "i$\$valid"), module.getInputNodes().map { it.name() })
        assertEquals(listOf("o", "p", "o$\$valid", "p$\$valid"), module.getOutputNodes().map { it.name() })
    }

    @Test
    fun `a sink built from two channels is a join, through an output port or a declared node`() {
        val viaOutput = compileFullExpectingDiagnostics("""
            interface pair { a: wire[8]; b: wire[8]; }
            $helpers

            @valid function v() i: pair => o: pair {
                i.a => inc()@valid => declare x: wire[8];
                i.b => inc()@valid => declare y: wire[8];
                x => o.a;
                y => o.b;
            }
        """.trimIndent())
        val join = assertIs<BuilderDiagnosticKind.ImplicitJoin>(viaOutput.single().kind)
        assertEquals(2, join.channelCount)

        // Assembling d from two channels is only a join once something consumes both parts of it.
        val viaDeclaredNode = compileFullExpectingDiagnostics("""
            interface pair { a: wire[8]; b: wire[8]; }
            $helpers

            function total() p: pair => o: wire[8] {
                p.a, p.b => add(8) => o;
            }

            @valid function v() i: pair => o: wire[8] {
                i.a => inc()@valid => declare x: wire[8];
                i.b => inc()@valid => declare y: wire[8];
                declare d: pair;
                x => d.a;
                y => d.b;
                d => total()@valid => o;
            }
        """.trimIndent())
        assertIs<BuilderDiagnosticKind.ImplicitJoin>(viaDeclaredNode.single().kind)
    }

    @Test
    fun `outputs of one island share a valid, so recombining them is not a join`() {
        compilesCleanly("""
            function split() i: wire[8] => lo: wire[4], hi: wire[4] {
                i[0:3] => lo;
                i[4:7] => hi;
            }

            @valid function v() i: wire[8] => o: wire[8] {
                i => split()@valid => declare lo: wire[4], declare hi: wire[4];
                lo => o[4:7];
                hi => o[0:3];
            }
        """.trimIndent())
    }

    // ---- @ready_valid ----

    @Test
    fun `an implicit fork in a @ready_valid body is rejected, including through a declared node`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @ready_valid function rv() i: wire[8] => a: wire[8], b: wire[8] {
                i => declare x: wire[8];
                x => inc()@ready_valid => a;
                x => inc()@ready_valid => b;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.ImplicitFork>(diagnostics.single().kind)
        assertEquals("i", kind.channel)
        assertEquals(2, kind.consumerCount)
    }

    @Test
    fun `fork primitives are only callable from @ready_valid`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            @valid function v() i: wire => a: wire, b: wire {
                i => fork_eager(wire, 2) => a, b;
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.IllegalProtocolCall>(diagnostics.single().kind)
    }

    // ---- @ready_valid handshake (stage 2c) ----

    @Test
    fun `a @ready_valid function gets a valid and a ready per port, ready flowing against the data`() {
        val result = Analyzer.analyzeFull("""
            $helpers

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => inc()@ready_valid => o;
            }
        """.trimIndent(), defaultOptions)

        assertEquals(emptyList(), result.diagnostics.map { it.message })
        val module = result.modules!!.single { it.invocation.gaplFunctionName == "rv" }
        assertEquals(listOf("i", "i$\$valid", "o$\$ready"), module.getInputNodes().map { it.name() })
        assertEquals(listOf("o", "o$\$valid", "i$\$ready"), module.getOutputNodes().map { it.name() })
    }

    @Test
    fun `islands in series and an eager fork that rejoins compile, with no false combinational loop`() {
        compilesCleanly("""
            $helpers

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => inc()@ready_valid => declare x: wire[8];
                x => fork_eager(wire[8], 2) => declare a: wire[8], declare b: wire[8];
                a => inc()@ready_valid => declare a2: wire[8];
                a2, b => sum()@ready_valid => o;
            }
        """.trimIndent())
    }

    @Test
    fun `a lazy fork whose branches rejoin is reported as a combinational loop`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => fork_lazy(wire[8], 2) => declare a: wire[8], declare b: wire[8];
                a => inc()@ready_valid => declare a2: wire[8];
                a2, b => sum()@ready_valid => o;
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.CombinationalLoop>(diagnostics.single().kind)
    }

    @Test
    fun `an unconsumed channel is an error in @ready_valid but not in @valid`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            function split() i: wire[8] => lo: wire[4], hi: wire[4] {
                i[0:3] => lo;
                i[4:7] => hi;
            }

            @ready_valid function rv() i: wire[8] => o: wire[4] {
                i => split()@ready_valid => o, declare unused: wire[4];
            }
        """.trimIndent())
        val kind = assertIs<BuilderDiagnosticKind.UnconsumedChannel>(diagnostics.single().kind)
        assertEquals("rv", kind.functionName)

        compilesCleanly("""
            function split() i: wire[8] => lo: wire[4], hi: wire[4] {
                i[0:3] => lo;
                i[4:7] => hi;
            }

            @valid function v() i: wire[8] => o: wire[4] {
                i => split()@valid => o, declare unused: wire[4];
            }
        """.trimIndent())
    }

    @Test
    fun `one channel into two inputs of the same island is an implicit fork`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            $helpers

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i, i => sum()@ready_valid => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.ImplicitFork>(diagnostics.single().kind)
        assertEquals("i", kind.channel)
    }

    @Test
    fun `under @ready_valid one island's outputs are separate channels, so recombining them is a join`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            function split() i: wire[8] => lo: wire[4], hi: wire[4] {
                i[0:3] => lo;
                i[4:7] => hi;
            }

            @ready_valid function rv() i: wire[8] => o: wire[8] {
                i => split()@ready_valid => declare lo: wire[4], declare hi: wire[4];
                lo => o[4:7];
                hi => o[0:3];
            }
        """.trimIndent())

        assertIs<BuilderDiagnosticKind.ImplicitJoin>(diagnostics.single().kind)
    }

    @Test
    fun `make_pair joins two channels into one`() {
        compilesCleanly(
            """
                @ready_valid function rv() a: wire[8], b: wire[4] => o: pair(wire[8], wire[4]) {
                    a, b => make_pair(wire[8], wire[4])@ready_valid => o;
                }
            """.trimIndent(),
            Analyzer.Options(includeStdLib = true),
        )
    }

    // ---- islands and arguments ----

    @Test
    fun `an island containing a register, even transitively, is rejected`() {
        val diagnostics = compileFullExpectingDiagnostics("""
            function delayed() i: wire[8] => o: wire[8] {
                i => register(wire[8]) => o;
            }

            function outer() i: wire[8] => o: wire[8] {
                i => delayed() => o;
            }

            @valid function v() i: wire[8] => o: wire[8] {
                i => outer()@valid => o;
            }
        """.trimIndent())

        val kind = assertIs<BuilderDiagnosticKind.IslandContainsRegister>(diagnostics.single().kind)
        assertEquals("outer", kind.calleeName)
        assertEquals("delayed", kind.registerFunctionName)
    }

    @Test
    fun `a register stays legal outside islands`() {
        compilesCleanly("""
            function delayed() i: wire[8] => o: wire[8] {
                i => register(wire[8]) => o;
            }

            function outer() i: wire[8] => o: wire[8] {
                i => delayed() => o;
            }
        """.trimIndent())
    }

    @Test
    fun `only @pure functions can be passed as arguments`() {
        val diagnostics = compileFullExpectingDiagnostics(
            """
                function inc() i: wire[8] => o: wire[8] {
                    i, literal(8, 1) => add(8) => o;
                }

                @valid function v() i: wire[8] => o: wire[8] {
                    i => inc()@valid => o;
                }

                function d() i: wire[8][2] => o: wire[8][2] {
                    i => vector_map(wire[8], wire[8], 2, v()) => o;
                }
            """.trimIndent(),
            Analyzer.Options(includeStdLib = true),
        )

        val kind = assertIs<BuilderDiagnosticKind.ProtocolFunctionArgument>(diagnostics.single().kind)
        assertEquals("v", kind.functionName)
    }
}
