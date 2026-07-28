package com.uabutler.simengine.eval

import java.math.BigInteger

/**
 * Bit 0 is the least significant bit, matching the compiler's Verilog codegen conventions
 * ([size-1:0] declarations, mux/demux slicing by `index * elementWidth`).
 */
fun bitsToUnsignedBigInteger(bits: List<Boolean>): BigInteger =
    bits.foldIndexed(BigInteger.ZERO) { i, acc, bit -> if (bit) acc.setBit(i) else acc }

fun unsignedBigIntegerToBits(value: BigInteger, size: Int): List<Boolean> {
    val truncated = value.mod(BigInteger.TWO.pow(size))
    return List(size) { i -> truncated.testBit(i) }
}
