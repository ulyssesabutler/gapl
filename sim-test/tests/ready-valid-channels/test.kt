package com.uabutler.simtest.tests.ready_valid_channels

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.ready_valid_channels.ReadyValidChannelsSimulator
import java.io.File
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Layer 3, stage 2c (type-system.md §5): a @ready_valid design driven like real hardware. Each input is
 * a source that offers items with random gaps and, once it asserts valid, holds it (and its data) until
 * accepted; each output is a sink whose ready drops at random. Every output's received sequence must
 * equal the model's, so nothing is lost, duplicated or reordered, however the stalls fall.
 *
 * The handshake ports appear in the wrapper as aValid / aReady (the IR names them a$$valid / a$$ready);
 * aReady is an output of the design, since ready flows against the data.
 */
class ReadyValidChannelsTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/ready-valid-channels/test.gapl").readText()
    }

    /** A source that may only withdraw valid once its item has been taken. */
    private class Source(val items: List<Int>, val random: Random) {
        var index = 0
        var holding = false
        fun offer(): Boolean {
            if (!holding) holding = index < items.size && random.nextDouble() < 0.7
            return holding
        }
        fun current() = items.getOrElse(index) { 0 }
        fun taken() { index++; holding = false }
    }

    private fun run(seed: Int) {
        val random = Random(seed)
        val count = 40
        val aItems = List(count) { random.nextInt(256) }
        val bItems = List(count) { random.nextInt(256) }
        val t = aItems.zip(bItems) { a, b -> (a + b) and 0xFF }
        val expected = mapOf(
            "s" to t.map { (it + 1) and 0xFF },
            "p" to t,
            "q" to t.map { it xor 0xFF },
            "u" to t,
            "v" to t,
        )

        val sim = ReadyValidChannelsSimulator(gaplSource())
        val a = Source(aItems, random)
        val b = Source(bItems, random)
        val received = expected.keys.associateWith { mutableListOf<Int>() }

        var cycles = 0
        while (received.values.any { it.size < count } && cycles < 5000) {
            val aValid = a.offer()
            val bValid = b.offer()
            sim.a = bits(a.current(), 8); sim.aValid = listOf(aValid)
            sim.b = bits(b.current(), 8); sim.bValid = listOf(bValid)
            val ready = expected.keys.associateWith { random.nextDouble() < 0.6 }
            sim.sReady = listOf(ready.getValue("s"))
            sim.pReady = listOf(ready.getValue("p"))
            sim.qReady = listOf(ready.getValue("q"))
            sim.uReady = listOf(ready.getValue("u"))
            sim.vReady = listOf(ready.getValue("v"))
            sim.settle()

            // Transfers happen where valid and ready are both high at this clock edge.
            val outputs = mapOf(
                "s" to (sim.sValid.single() to sim.s),
                "p" to (sim.pValid.single() to sim.p),
                "q" to (sim.qValid.single() to sim.q),
                "u" to (sim.uValid.single() to sim.u),
                "v" to (sim.vValid.single() to sim.v),
            )
            outputs.forEach { (name, output) ->
                val (valid, data) = output
                if (valid && ready.getValue(name)) received.getValue(name) += data.toIntValue()
            }
            val aTaken = aValid && sim.aReady.single()
            val bTaken = bValid && sim.bReady.single()

            sim.tick()
            if (aTaken) a.taken()
            if (bTaken) b.taken()
            cycles++
        }

        expected.forEach { (name, items) -> assertEquals(items, received.getValue(name), "output $name (seed $seed)") }
    }

    @Test
    fun `every output receives exactly the model's sequence under random gaps and backpressure`() {
        listOf(1, 2, 3, 4, 5).forEach(::run)
    }
}
