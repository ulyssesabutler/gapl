package com.uabutler.interpreter

import com.uabutler.simengine.PortValue
import com.uabutler.simgen.PortShape
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortValueJsonTest {

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `boolean decodes for a 1-bit leaf`() {
        assertEquals(PortValue.Bits(listOf(true)), PortValueJson.decode(PortShape.Leaf(1), json("true"), "i"))
        assertEquals(PortValue.Bits(listOf(false)), PortValueJson.decode(PortShape.Leaf(1), json("false"), "i"))
    }

    @Test
    fun `boolean is rejected for a wider leaf`() {
        assertFailsWith<IllegalStateException> { PortValueJson.decode(PortShape.Leaf(8), json("true"), "i") }
    }

    @Test
    fun `hex string decodes MSB-first`() {
        // 0x0A = 0b00001010, bit 0 (LSB) = false, bit 3 = true, rest false.
        val expected = PortValue.Bits(listOf(false, true, false, true, false, false, false, false))
        assertEquals(expected, PortValueJson.decode(PortShape.Leaf(8), json("\"0x0A\""), "i"))
    }

    @Test
    fun `bit string decodes MSB-first`() {
        // 0b1010 = 10 = 0b1010; bit 0 (LSB) = false, bit 1 = true, bit 2 = false, bit 3 (MSB) = true.
        val expected = PortValue.Bits(listOf(false, true, false, true))
        assertEquals(expected, PortValueJson.decode(PortShape.Leaf(4), json("\"0b1010\""), "i"))
    }

    @Test
    fun `plain string decodes as UTF-8, first character most significant`() {
        // 'A' = 0x41, 'B' = 0x42 - "AB" as a 16-bit value is 0x4142, matching left-to-right reading.
        val decoded = PortValueJson.decode(PortShape.Leaf(16), json("\"AB\""), "i")
        assertEquals(PortValueJson.decode(PortShape.Leaf(16), json("\"0x4142\""), "i"), decoded)
    }

    @Test
    fun `UTF-8 string with the wrong byte count is rejected`() {
        assertFailsWith<IllegalStateException> { PortValueJson.decode(PortShape.Leaf(8), json("\"AB\""), "i") }
    }

    @Test
    fun `record decodes each field by key`() {
        val shape = PortShape.Record(mapOf("a" to PortShape.Leaf(8), "b" to PortShape.Leaf(4)))
        val decoded = PortValueJson.decode(shape, json("""{"a": "0x0A", "b": "0b1001"}"""), "i")
        assertEquals(
            PortValue.Fields(
                mapOf(
                    "a" to PortValueJson.decode(PortShape.Leaf(8), json("\"0x0A\""), "a"),
                    "b" to PortValueJson.decode(PortShape.Leaf(4), json("\"0b1001\""), "b"),
                )
            ),
            decoded,
        )
    }

    @Test
    fun `record with an unknown field is rejected`() {
        val shape = PortShape.Record(mapOf("a" to PortShape.Leaf(8)))
        assertFailsWith<IllegalStateException> { PortValueJson.decode(shape, json("""{"a": "0x0A", "typo": "0x00"}"""), "i") }
    }

    @Test
    fun `record with a missing field is rejected`() {
        val shape = PortShape.Record(mapOf("a" to PortShape.Leaf(8), "b" to PortShape.Leaf(4)))
        assertFailsWith<IllegalStateException> { PortValueJson.decode(shape, json("""{"a": "0x0A"}"""), "i") }
    }

    @Test
    fun `vector decodes each element positionally`() {
        val shape = PortShape.Vector(PortShape.Leaf(4), 2)
        val decoded = PortValueJson.decode(shape, json("""["0x1", "0x2"]"""), "i")
        assertEquals(
            PortValue.Elements(
                listOf(
                    PortValueJson.decode(PortShape.Leaf(4), json("\"0x1\""), "i[0]"),
                    PortValueJson.decode(PortShape.Leaf(4), json("\"0x2\""), "i[1]"),
                )
            ),
            decoded,
        )
    }

    @Test
    fun `vector with the wrong length is rejected`() {
        val shape = PortShape.Vector(PortShape.Leaf(4), 3)
        assertFailsWith<IllegalStateException> { PortValueJson.decode(shape, json("""["0x1", "0x2"]"""), "i") }
    }

    @Test
    fun `toDisplayString renders bits as hex`() {
        assertEquals("0x0a", PortValueJson.toDisplayString(PortValueJson.decode(PortShape.Leaf(8), json("\"0x0A\""), "i")))
    }
}
