package com.uabutler.vcd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentifierCodesTest {

    @Test
    fun `hand computed indices`() {
        assertEquals("!", IdentifierCodes.forIndex(0))
        assertEquals("\"", IdentifierCodes.forIndex(1))
        assertEquals("~", IdentifierCodes.forIndex(93))
        assertEquals("\"!", IdentifierCodes.forIndex(94))
        assertEquals("#!", IdentifierCodes.forIndex(188))
    }

    @Test
    fun `unique across a range spanning multiple digit widths`() {
        val codes = (0..999).map { IdentifierCodes.forIndex(it) }
        assertEquals(1000, codes.toSet().size)
    }

    @Test
    fun `negative index throws`() {
        assertFailsWith<IllegalArgumentException> { IdentifierCodes.forIndex(-1) }
    }
}
