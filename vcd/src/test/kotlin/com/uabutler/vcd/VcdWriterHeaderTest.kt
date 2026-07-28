package com.uabutler.vcd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VcdWriterHeaderTest {

    @Test
    fun `date null omits the date block entirely`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 1 ! clk \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `date present appears before version`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink, date = "2026-07-27")
        writer.declareSignal(emptyList(), "clk", 1)
        writer.writeHeader()

        assertEquals(
            "\$date 2026-07-27 \$end\n" +
                "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 1 ! clk \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `custom timescale and version appear verbatim`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink, timescale = "10ps", version = "my sim v2")
        writer.writeHeader()

        assertEquals(
            "\$version my sim v2 \$end\n" +
                "\$timescale 10ps \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `flat signals produce no scope or upscope lines`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(emptyList(), "a", 1)
        writer.declareSignal(emptyList(), "b", 4)
        writer.writeHeader()

        val text = sink.toString()
        assertEquals(false, text.contains("\$scope"))
        assertEquals(false, text.contains("\$upscope"))
        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 1 ! a \$end\n" +
                "\$var wire 4 \" b \$end\n" +
                "\$enddefinitions \$end\n",
            text,
        )
    }

    @Test
    fun `single level scope with multiple signals`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(listOf("cpu"), "x", 1)
        writer.declareSignal(listOf("cpu"), "y", 1)
        writer.writeHeader()

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$scope module cpu \$end\n" +
                "\$var wire 1 ! x \$end\n" +
                "\$var wire 1 \" y \$end\n" +
                "\$upscope \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `deep nesting preserves sibling order`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(listOf("cpu", "alu", "adder"), "sum", 4)
        writer.writeHeader()

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$scope module cpu \$end\n" +
                "\$scope module alu \$end\n" +
                "\$scope module adder \$end\n" +
                "\$var wire 4 ! sum \$end\n" +
                "\$upscope \$end\n" +
                "\$upscope \$end\n" +
                "\$upscope \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `sibling scopes preserve declaration order not alphabetical`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(listOf("b"), "x", 1)
        writer.declareSignal(listOf("a"), "y", 1)
        writer.writeHeader()

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$scope module b \$end\n" +
                "\$var wire 1 ! x \$end\n" +
                "\$upscope \$end\n" +
                "\$scope module a \$end\n" +
                "\$var wire 1 \" y \$end\n" +
                "\$upscope \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `mixed root and nested signals in one writer`() {
        val sink = StringBuilder()
        val writer = VcdWriter(sink)
        writer.declareSignal(emptyList(), "clk", 1)
        writer.declareSignal(listOf("cpu"), "x", 1)
        writer.writeHeader()

        assertEquals(
            "\$version simengine \$end\n" +
                "\$timescale 1ns \$end\n" +
                "\$var wire 1 ! clk \$end\n" +
                "\$scope module cpu \$end\n" +
                "\$var wire 1 \" x \$end\n" +
                "\$upscope \$end\n" +
                "\$enddefinitions \$end\n",
            sink.toString(),
        )
    }

    @Test
    fun `declareSignal after writeHeader throws`() {
        val writer = VcdWriter(StringBuilder())
        writer.writeHeader()
        assertFailsWith<IllegalStateException> { writer.declareSignal(emptyList(), "late", 1) }
    }

    @Test
    fun `writeHeader called twice throws`() {
        val writer = VcdWriter(StringBuilder())
        writer.writeHeader()
        assertFailsWith<IllegalStateException> { writer.writeHeader() }
    }

    @Test
    fun `declareSignal with whitespace in name throws`() {
        val writer = VcdWriter(StringBuilder())
        assertFailsWith<IllegalArgumentException> { writer.declareSignal(emptyList(), "my signal", 1) }
    }

    @Test
    fun `declareSignal with whitespace in scope segment throws`() {
        val writer = VcdWriter(StringBuilder())
        assertFailsWith<IllegalArgumentException> { writer.declareSignal(listOf("my scope"), "x", 1) }
    }
}
