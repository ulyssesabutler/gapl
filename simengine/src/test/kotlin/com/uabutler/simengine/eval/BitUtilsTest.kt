package com.uabutler.simengine.eval

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class BitUtilsTest {

    @Test
    fun `bit 0 is least significant`() {
        // 0b0101 = 5, bit 0 (LSB) set, bit 2 set
        val bits = listOf(true, false, true, false)
        assertEquals(BigInteger.valueOf(5), bitsToUnsignedBigInteger(bits))
    }

    @Test
    fun `round trips through a width larger than 64 bits`() {
        val value = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7))
        val bits = unsignedBigIntegerToBits(value, 128)
        assertEquals(value, bitsToUnsignedBigInteger(bits))
        assertEquals(128, bits.size)
    }

    @Test
    fun `truncates values wider than the requested size`() {
        val value = BigInteger.valueOf(0b10110) // 22, needs 5 bits
        val bits = unsignedBigIntegerToBits(value, 4) // keep only the low 4 bits: 0b0110 = 6
        assertEquals(BigInteger.valueOf(0b0110), bitsToUnsignedBigInteger(bits))
        assertEquals(4, bits.size)
    }

    @Test
    fun `wraps negative results into unsigned range via mod`() {
        val negative = BigInteger.valueOf(-1)
        val bits = unsignedBigIntegerToBits(negative, 4)
        assertEquals(BigInteger.valueOf(0b1111), bitsToUnsignedBigInteger(bits))
    }
}
