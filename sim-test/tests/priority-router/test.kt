package com.uabutler.simtest.tests.priority_router

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.priority_router.PriorityRouterSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/priority-router/test.cpp — but corrected. The
 * original C++ test has a bug (`if (selector == -1)` compares an uninitialized uint32_t instead of
 * the loop variable `selectorShift`), so it only ever exercises the bitwise_or default path, never
 * the add/multiply/subtract/bitwise_and priority branches it claims to check. This test instead
 * sweeps every possible 4-bit selector value against the real PriorityFunction semantics (confirmed
 * directly from PredefinedFunctionEvaluator.kt): the lowest-index condition that's true wins,
 * otherwise the default. test.gapl contains no register() calls, so this is purely combinational —
 * one settle() per stimulus, no tick() needed.
 */
class PriorityRouterTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/priority-router/test.gapl").readText()
    }

    @Test
    fun `lowest set selector bit wins, else the bitwise_or default`() {
        val sim = PriorityRouterSimulator(gaplSource())

        for (selector in 0..15) {
            for (lhs in 0..9) {
                for (rhs in 0..lhs) {
                    sim.selector = bits(selector, 4)
                    sim.i1 = bits(lhs, 8)
                    sim.i2 = bits(rhs, 8)
                    sim.settle()

                    val expected = when {
                        selector and 0b0001 != 0 -> lhs + rhs
                        selector and 0b0010 != 0 -> lhs * rhs
                        selector and 0b0100 != 0 -> lhs - rhs
                        selector and 0b1000 != 0 -> lhs and rhs
                        else -> lhs or rhs
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
