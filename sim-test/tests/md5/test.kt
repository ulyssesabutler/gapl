package com.uabutler.simtest.tests.md5

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.md5.Md5Simulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/md5/test.cpp: same known-answer vectors, reused
 * verbatim, plus a port of that file's md5PadToSingleBlockHex (single-block messages only). test.gapl
 * contains no register() calls - the hash pipeline is purely combinational - so one settle() per
 * vector is enough, no tick() loop like the C++ harness needs.
 */
class Md5Test {

    private data class TestVector(val iHex: String, val expectedOHex: String)

    private val testVectors = listOf(
        TestVector("000102030405060708090a0b0c0d0e0f", "1ac1ef01e96caf1be0d329331a4fc2a8"),
        TestVector("00112233445566778899aabbccddeeff", "6e8311168ee16d6aa1aa48c64145003c"),
        TestVector("00000000000000000000000000000000", "4ae71336e44bf9bf79d2752e234818a5"),
        TestVector("30313233343536373839616263646566", "4032af8d61035123906e58e067140cc5"),
    )

    private fun hexToBytes(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "odd number of hex digits" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** MD5 padding for single-block messages (padded message is exactly 64 bytes). */
    private fun padToSingleBlock(messageHex: String): ByteArray {
        val message = hexToBytes(messageHex).toMutableList()
        val originalLenBits = message.size.toLong() * 8

        message.add(0x80.toByte())
        while (message.size % 64 != 56) {
            message.add(0)
            check(message.size <= 64) { "message requires multiple 512-bit blocks after padding" }
        }
        for (i in 0 until 8) {
            message.add(((originalLenBits shr (8 * i)) and 0xFF).toByte())
        }
        check(message.size == 64) { "padded block is not 64 bytes" }
        return message.toByteArray()
    }

    private fun bytesToBits(bytes: ByteArray, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger(1, bytes), size)

    private fun List<Boolean>.toHex(hexChars: Int): String = bitsToUnsignedBigInteger(this).toString(16).padStart(hexChars, '0')

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/md5/test.gapl").readText()
    }

    @Test
    fun `hashes known-answer single-block MD5 test vectors`() {
        val sim = Md5Simulator(gaplSource())

        for (tv in testVectors) {
            val paddedBlock = padToSingleBlock(tv.iHex)
            sim.i = bytesToBits(paddedBlock, 512)
            sim.settle()

            assertEquals(tv.expectedOHex, sim.o.toHex(32), "input=${tv.iHex}")
        }
    }
}
