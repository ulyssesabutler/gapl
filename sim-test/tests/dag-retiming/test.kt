package com.uabutler.simtest.tests.dag_retiming

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.dag_retiming.DagRetimingSimulator
import java.io.File
import java.math.BigInteger
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/dag-retiming/test.cpp. That C++ test discovers the
 * pipeline latency empirically because the compiler's DAG retiming solver is free to relocate
 * registers for timing closure. simengine interprets test.gapl's untransformed netlist directly, so
 * no such relocation happens: the source has exactly one register() call, wrapped by four unary()
 * (bitwise_not) stages — an even count, composing to the identity function — so the latency is
 * always exactly 1 tick, the same fixed-latency pattern simple-register's own test already uses.
 */
class DagRetimingTest {

    private fun bits(value: Long, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value), size)

    private fun List<Boolean>.toLongValue(): Long = bitsToUnsignedBigInteger(this).toLong()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/dag-retiming/test.gapl").readText()
    }

    @Test
    fun `identity chain delays the input by exactly one tick`() {
        val sim = DagRetimingSimulator(gaplSource())

        val rng = Random(24680)
        // o is downstream of the register through *three* unary() (bitwise_not) stages, not all
        // four - only the full i-to-o path (one unary before the register, three after) composes to
        // identity. So before the first tick(), while the register still holds its initial all-zero
        // state, a settle() makes o read as bitwise_not(0) = all-ones, not the raw input.
        var previous = 0xFFFFFFFFL
        repeat(16) {
            val value = rng.nextInt().toLong() and 0xFFFFFFFFL
            sim.i = bits(value, 32)

            sim.settle()
            assertEquals(previous, sim.o.toLongValue(), "output should still reflect the prior tick before latching")

            sim.tick()
            assertEquals(value, sim.o.toLongValue(), "output should reflect this tick's input immediately after latching")

            previous = value
        }
    }
}
