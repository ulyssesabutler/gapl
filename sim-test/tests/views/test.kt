package com.uabutler.simtest.tests.views

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.views.ViewsSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Views (type-system.md §3) lower to plain wiring in NodeBuilder, so this checks the wiring lands on
 * the right bits: project picks out exactly the named slices, overlay takes the view's fields from the
 * view and every other bit from the base, zeros is all zero, and a project passed to vector_map keeps
 * its mapping. test.gapl contains no register() calls, so one settle() per stimulus is enough.
 */
class ViewsTest {

    private fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

    private fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/views/test.gapl").readText()
    }

    private fun beat(data: Int, keep: Int) = ViewsSimulator.I(data = bits(data, 16), keep = bits(keep, 2))

    @Test
    fun `project, overlay and zeros place every bit correctly`() {
        val sim = ViewsSimulator(gaplSource())

        sim.i = beat(data = 0xA5C3, keep = 0b10)
        sim.nib = bits(0x6, 4)
        sim.pair = listOf(
            ViewsSimulator.Pair(data = bits(0x1234, 16), keep = bits(0b00, 2)),
            ViewsSimulator.Pair(data = bits(0xF00D, 16), keep = bits(0b11, 2)),
        )
        sim.settle()

        // project: lo = data[0:3], hi = data[12:15], k = keep[1]
        assertEquals(0x3, sim.h.lo.toIntValue())
        assertEquals(0xA, sim.h.hi.toIntValue())
        assertEquals(1, sim.h.k.toIntValue())

        // overlay: only data[0:3] changes (to nib); hi and k are written back unchanged, and every
        // bit the view doesn't name comes straight from the base.
        assertEquals(0xA5C6, sim.o.data.toIntValue())
        assertEquals(0b10, sim.o.keep.toIntValue())

        assertEquals(0, sim.z.data.toIntValue())
        assertEquals(0, sim.z.keep.toIntValue())

        assertEquals(listOf(Triple(0x4, 0x1, 0), Triple(0xD, 0xF, 1)), sim.hs.map {
            Triple(it.lo.toIntValue(), it.hi.toIntValue(), it.k.toIntValue())
        })
    }
}
