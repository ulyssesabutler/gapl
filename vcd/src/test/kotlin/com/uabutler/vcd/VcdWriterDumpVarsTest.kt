package com.uabutler.vcd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VcdWriterDumpVarsTest {

    @Test
    fun `dumpvars block for a 1-bit and a multi-bit signal`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        val data = writer.declareSignal(emptyList(), "data", 4)
        writer.writeHeader()
        sink.clear()

        writer.dumpInitialValues(mapOf(clk to listOf(false), data to listOf(false, false, false, false)))

        assertEquals(
            "\$dumpvars\n" +
                "0!\n" +
                "b0000 \"\n" +
                "\$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `missing a declared signal throws`() {
        val writer = VcdWriter(StringBuilder())
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        writer.declareSignal(emptyList(), "data", 4)
        writer.writeHeader()

        assertFailsWith<IllegalArgumentException> {
            writer.dumpInitialValues(mapOf(clk to listOf(false)))
        }
    }

    @Test
    fun `wrong width value throws`() {
        val writer = VcdWriter(StringBuilder())
        val clk = writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()

        assertFailsWith<IllegalArgumentException> {
            writer.dumpInitialValues(mapOf(clk to listOf(false, false)))
        }
    }

    @Test
    fun `dumpInitialValues before writeHeader throws`() {
        val writer = VcdWriter(StringBuilder())
        val clk = writer.declareSignal(emptyList(), "clk", 1)

        assertFailsWith<IllegalStateException> {
            writer.dumpInitialValues(mapOf(clk to listOf(false)))
        }
    }
}
