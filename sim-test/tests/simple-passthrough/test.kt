package com.uabutler.simtest.tests.simple_passthrough

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.simple_passthrough.SimplePassthroughSimulator
import java.io.File
import java.math.BigInteger
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/simple-passthrough/test.cpp: drives the design
 * through the simengine/simgen pipeline instead of a Verilator-compiled C++ model.
 */
class SimplePassthroughTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/simple-passthrough/test.gapl").readText()
    }

    @Test
    fun `output always reflects the current input with no delay`() {
        val sim = SimplePassthroughSimulator(gaplSource())

        val rng = Random(12345)
        repeat(10) {
            val value = rng.nextInt(256)
            sim.i = bits(value, 8)

            sim.settle()
            assertEquals(value, sim.o.toIntValue())

            sim.tick()
            assertEquals(value, sim.o.toIntValue())
        }
    }
}
