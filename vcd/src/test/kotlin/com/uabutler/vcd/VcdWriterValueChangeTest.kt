package com.uabutler.vcd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VcdWriterValueChangeTest {

    @Test
    fun `1-bit value line has no space before the id`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()
        sink.clear()

        writer.advanceTime(1)
        writer.writeValue(clk, listOf(true))
        writer.advanceTime(2)
        writer.writeValue(clk, listOf(false))

        assertEquals("#1\n1!\n#2\n0!\n", sink.toString())
    }

    @Test
    fun `multi-bit value line has a space before the id`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val data = writer.declareSignal(emptyList(), "data", 4)
        writer.writeHeader()
        sink.clear()

        writer.advanceTime(1)
        writer.writeValue(data, listOf(true, true, false, false)) // LSB-first: value 3 -> MSB-first "0011"

        assertEquals("#1\nb0011 !\n", sink.toString())
    }

    @Test
    fun `LSB-first input renders MSB-first in VCD output`() {
        // A non-palindromic pattern: a forgotten OR backwards reversal would both produce
        // plausible-looking-but-wrong output here, unlike a palindrome.
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val data = writer.declareSignal(emptyList(), "data", 4)
        writer.writeHeader()
        sink.clear()

        writer.advanceTime(1)
        writer.writeValue(data, listOf(true, false, false, false)) // LSB-first: value 1 -> MSB-first "0001"

        assertEquals("#1\nb0001 !\n", sink.toString())
    }

    @Test
    fun `advanceTime requires strictly increasing time`() {
        val writer = VcdWriter(StringBuilder())
        writer.writeHeader()
        writer.advanceTime(5)
        assertFailsWith<IllegalArgumentException> { writer.advanceTime(5) }
        assertFailsWith<IllegalArgumentException> { writer.advanceTime(4) }
    }

    @Test
    fun `advanceTime before writeHeader throws`() {
        val writer = VcdWriter(StringBuilder())
        assertFailsWith<IllegalStateException> { writer.advanceTime(1) }
    }

    @Test
    fun `writeValue before writeHeader throws`() {
        val writer = VcdWriter(StringBuilder())
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        assertFailsWith<IllegalStateException> { writer.writeValue(clk, listOf(true)) }
    }

    @Test
    fun `identical writeValue calls after one advanceTime emit only once`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()
        sink.clear()

        writer.advanceTime(1)
        writer.writeValue(clk, listOf(true))
        writer.writeValue(clk, listOf(true)) // same value again - should no-op

        assertEquals("#1\n1!\n", sink.toString())
    }

    @Test
    fun `writeValue with wrong width throws`() {
        val writer = VcdWriter(StringBuilder())
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()
        writer.advanceTime(1)
        assertFailsWith<IllegalArgumentException> { writer.writeValue(clk, listOf(true, false)) }
    }

    @Test
    fun `full end to end example`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        val data = writer.declareSignal(listOf("cpu", "alu"), "data", 4)
        writer.writeHeader()
        writer.dumpInitialValues(mapOf(clk to listOf(false), data to listOf(false, false, false, false)))
        writer.advanceTime(5)
        writer.writeValue(clk, listOf(true))
        writer.writeValue(data, listOf(true, false, true, false)) // LSB-first: value 5 -> MSB-first "0101"

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 1 ! clk \$end\n" +
                "\$scope module cpu \$end\n" +
                "\$scope module alu \$end\n" +
                "\$var wire 4 \" data \$end\n" +
                "\$upscope \$end\n" +
                "\$upscope \$end\n" +
                "\$enddefinitions \$end\n" +
                "\$dumpvars\n" +
                "0!\n" +
                "b0000 \"\n" +
                "\$end\n" +
                "#5\n" +
                "1!\n" +
                "b0101 \"\n",
            sink.toString(),
        )
    }
}
