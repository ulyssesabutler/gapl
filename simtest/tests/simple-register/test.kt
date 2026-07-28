package com.uabutler.simharness.tests.simple_register

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.simple_register.SimpleRegisterSimulator
import java.io.File
import java.math.BigInteger
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of simtest/tests/simple-register/test.cpp: drives the design through the
 * simengine/simgen pipeline instead of a Verilator-compiled C++ model. Uses Kotlin's own PRNG
 * rather than the C++ mt19937 sequence bit-for-bit — Engine.tick()/settle() already established
 * parity with the Verilog ground truth at the simengine phase; this test's job is proving the
 * simgen-generated wrapper itself works end to end, not re-proving interpreter/Verilog parity.
 */
class SimpleRegisterTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val rootDir = System.getProperty("gaplRootDir") ?: error("gaplRootDir system property not set")
        return File(rootDir, "simtest/tests/simple-register/test.gapl").readText()
    }

    @Test
    fun `register delays the input by exactly one tick`() {
        val sim = SimpleRegisterSimulator(gaplSource())

        assertEquals(0, sim.o.toIntValue())

        val rng = Random(12345)
        var previous = 0
        repeat(10) {
            val value = rng.nextInt(256)
            sim.i = bits(value, 8)

            sim.settle()
            assertEquals(previous, sim.o.toIntValue(), "output should still reflect the prior tick before latching")

            sim.tick()
            assertEquals(value, sim.o.toIntValue(), "output should reflect this tick's input immediately after latching")

            previous = value
        }
    }
}
