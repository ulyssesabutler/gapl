package com.uabutler.simgen

import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RootModuleResolverTest {

    /** Returns the *root* modules (matching what RootModuleResolver.resolve actually expects). */
    private fun compileRoots(gapl: String): List<Module> {
        val result = Analyzer.analyzeFull(gapl, Analyzer.Options(includeStdLib = false))
        assertTrue(result.diagnostics.isEmpty(), "unexpected diagnostics: ${result.diagnostics}")
        return InvocationGraph(result.modules!!).rootModules()
    }

    private val twoRootsGapl = """
        function a() i: wire[4] => o: wire[4] {
            i => o;
        }
        function b() i: wire[4] => o: wire[4] {
            i => o;
        }
    """.trimIndent()

    @Test
    fun `resolves by name when it matches a root module`() {
        val modules = compileRoots(twoRootsGapl)
        val resolved = RootModuleResolver.resolve(modules, "b")
        assertEquals("b", resolved.invocation.gaplFunctionName)
    }

    @Test
    fun `name with no match lists available root modules`() {
        val modules = compileRoots(twoRootsGapl)
        val exception = assertFailsWith<IllegalStateException> { RootModuleResolver.resolve(modules, "c") }
        assertTrue(exception.message!!.contains("a"))
        assertTrue(exception.message!!.contains("b"))
    }

    @Test
    fun `no name and exactly one root module resolves it`() {
        val modules = compileRoots(
            """
            function test() i: wire[8] => o: wire[8] {
                i => o;
            }
            """.trimIndent(),
        )
        val resolved = RootModuleResolver.resolve(modules, null)
        assertEquals("test", resolved.invocation.gaplFunctionName)
    }

    @Test
    fun `no name and multiple root modules throws listing them`() {
        val modules = compileRoots(twoRootsGapl)
        val exception = assertFailsWith<IllegalStateException> { RootModuleResolver.resolve(modules, null) }
        assertTrue(exception.message!!.contains("a"))
        assertTrue(exception.message!!.contains("b"))
    }

    @Test
    fun `no name and zero root modules throws`() {
        // A cyclic-invocation fixture can't reach this branch: ModuleBuilder catches recursion as a
        // diagnostic before analyzeFull ever returns non-null modules, so zero root modules is only
        // reachable as a direct unit-level call, not through a real compiled fixture.
        assertFailsWith<IllegalStateException> { RootModuleResolver.resolve(emptyList(), null) }
    }
}
