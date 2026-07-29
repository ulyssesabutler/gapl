package com.uabutler.simtest.tests.mux_demux

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.mux_demux.MuxDemuxSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/mux-demux/test.cpp: drives the design through the
 * simengine/simgen pipeline instead of a Verilator-compiled C++ model. test.gapl contains no
 * register() calls, so the demux/mux/arithmetic chain is purely combinational — one settle() per
 * stimulus, no tick() needed.
 */
class MuxDemuxTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/mux-demux/test.gapl").readText()
    }

    @Test
    fun `selector routes i1,i2 through the matching arithmetic operation`() {
        val sim = MuxDemuxSimulator(gaplSource())

        for (selector in 0..3) {
            for (lhs in 0..9) {
                for (rhs in 0..lhs) {
                    sim.selector = bits(selector, 2)
                    sim.i1 = bits(lhs, 8)
                    sim.i2 = bits(rhs, 8)
                    sim.settle()

                    val expected = when (selector) {
                        0 -> lhs + rhs
                        1 -> lhs * rhs
                        2 -> lhs - rhs
                        3 -> lhs and rhs
                        else -> error("unreachable")
                    }

                    assertEquals(
                        expected and 0xFF,
                        sim.o.toIntValue(),
                        "selector=$selector lhs=$lhs rhs=$rhs",
                    )
                }
            }
        }
    }
}
