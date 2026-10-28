package com.uabutler.simtest.tests.valid_channels

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.valid_channels.ValidChannelsSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Layer 3, stage 2b (type-system.md §5): a @valid function's `valid` ports, wired by the compiler. Before
 * retiming every island is combinational, so each output's valid is its source channel's valid this same
 * cycle - one settle() per stimulus is enough. The handshake ports appear in the wrapper as pValid,
 * aValid, ... (the IR names them p$$valid, a$$valid).
 */
class ValidChannelsTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/valid-channels/test.gapl").readText()
    }

    private fun ValidChannelsSimulator.valids() = listOf(aValid, bValid, cValid, dValid).map { it.single() }

    @Test
    fun `valid follows each output's source channel, and a constant island is always valid`() {
        val sim = ValidChannelsSimulator(gaplSource())
        sim.p = ValidChannelsSimulator.P(a = bits(10, 8), b = bits(20, 8))

        // A gap on the input: everything derived from p is invalid; the constant still is valid.
        sim.pValid = listOf(false)
        sim.settle()
        assertEquals(listOf(false, false, true, false), sim.valids())

        sim.pValid = listOf(true)
        sim.settle()
        assertEquals(listOf(true, true, true, true), sim.valids())
        assertEquals(11, sim.a.toIntValue()) // through the nested @valid call
        assertEquals(21, sim.b.toIntValue())
        assertEquals(42, sim.c.toIntValue())
        assertEquals(11, sim.d.toIntValue()) // the other branch of the fork

        // And back to a gap.
        sim.pValid = listOf(false)
        sim.settle()
        assertEquals(listOf(false, false, true, false), sim.valids())
    }
}
