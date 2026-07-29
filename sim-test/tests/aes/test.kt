package com.uabutler.simtest.tests.aes

import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.generated.aes.AesSimulator
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin-native analogue of verilator-test/tests/aes/test.cpp: same FIPS-197 AES-128 known-answer
 * vectors, reused verbatim (already-proven expected outputs for this exact design against
 * Verilator, matching the reasoning simple-register/test.kt's own comment documents). test.gapl
 * contains no register() calls - the whole encrypt pipeline is purely combinational - so one
 * settle() per vector is enough, no tick() loop like the C++ harness needs.
 */
class AesTest {

    private data class TestVector(val iHex: String, val keyHex: String, val expectedOHex: String)

    private val testVectors = listOf(
        TestVector(
            "000102030405060708090a0b0c0d0e0f",
            "000102030405060708090a0b0c0d0e0f",
            "0a940bb5416ef045f1c39458c653ea5a",
        ),
        TestVector(
            "00112233445566778899aabbccddeeff",
            "00112233445566778899aabbccddeeff",
            "62f679be2bf0d931641e039ca3401bb2",
        ),
        TestVector(
            "00112233445566778899aabbccddeeff",
            "000102030405060708090a0b0c0d0e0f",
            "69c4e0d86a7b0430d8cdb78070b4c55a",
        ),
        TestVector(
            "000102030405060708090a0b0c0d0e0f",
            "00112233445566778899aabbccddeeff",
            "279fb74a7572135e8f9b8ef6d1eee003",
        ),
        TestVector(
            "00000000000000000000000000000000",
            "00000000000000000000000000000000",
            "66e94bd4ef8a2c3b884cfa59ca342b2e",
        ),
    )

    private fun hexToBits(hex: String, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger(hex, 16), size)

    private fun List<Boolean>.toHex(hexChars: Int): String = bitsToUnsignedBigInteger(this).toString(16).padStart(hexChars, '0')

    private fun gaplSource(): String {
        val root = System.getProperty("simTestRoot") ?: error("simTestRoot system property not set")
        return File(root, "tests/aes/test.gapl").readText()
    }

    @Test
    fun `encrypts known-answer FIPS-197 AES-128 test vectors`() {
        val sim = AesSimulator(gaplSource())

        for (tv in testVectors) {
            sim.i = hexToBits(tv.iHex, 128)
            sim.key = hexToBits(tv.keyHex, 128)
            sim.settle()

            assertEquals(tv.expectedOHex, sim.o.toHex(32), "input=${tv.iHex} key=${tv.keyHex}")
        }
    }
}
