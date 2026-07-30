package com.uabutler.simtest.tests.composite_interface

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.composite_interface.CompositeInterfaceSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Purpose-built fixture for the composite-interface simgen support - nothing in verilator-test's own
 * corpus exercises a top-level array-of-record port (pair_type[3]), so this proves the generated
 * List<I>/List<O> nested-class API and its structure-of-arrays <-> Kotlin marshaling round-trip
 * correctly through the real, CLI-generated wrapper (not just Engine directly). test.gapl contains no
 * register() calls, so one settle() per stimulus is enough.
 */
class CompositeInterfaceTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/composite-interface/test.gapl").readText()
    }

    @Test
    fun `array-of-record port passes each element through independently`() {
        val sim = CompositeInterfaceSimulator(gaplSource())

        val elements = listOf(
            CompositeInterfaceSimulator.I(a = bits(10, 8), b = bits(1, 4)),
            CompositeInterfaceSimulator.I(a = bits(200, 8), b = bits(9, 4)),
            CompositeInterfaceSimulator.I(a = bits(255, 8), b = bits(15, 4)),
        )
        sim.i = elements
        sim.settle()

        val expected = listOf(10 to 1, 200 to 9, 255 to 15)
        sim.o.map { it.a.toIntValue() to it.b.toIntValue() }.zip(expected).forEach { (actual, exp) ->
            assertEquals(exp, actual)
        }
    }
}
