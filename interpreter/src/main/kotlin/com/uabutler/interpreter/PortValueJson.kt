package com.uabutler.interpreter

import com.uabutler.simengine.PortValue
import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.PortShape
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigInteger

/**
 * Converts between JSON (as accepted from a cycles file) and simengine's PortValue tree, driven by
 * a port's PortShape. A leaf value may be a JSON boolean (only valid for a 1-bit port), a "0x"/"0X"
 * hex string, a "0b"/"0B" bit string, or any other string interpreted as UTF-8 bytes. All three
 * string forms read MSB-first/left-to-right, matching standard positional notation, then convert to
 * the engine's own LSB-first bit list via unsignedBigIntegerToBits. Composite shapes mirror
 * PortValue's own structure directly: a JSON object for a Record, a JSON array for a Vector.
 */
object PortValueJson {
    fun decode(shape: PortShape, json: JsonElement, path: String): PortValue = when (shape) {
        is PortShape.Leaf -> PortValue.Bits(decodeLeaf(shape.width, json, path))
        is PortShape.Record -> {
            val obj = requireJsonObject(json, path)
            val unknown = obj.keys - shape.fields.keys
            check(unknown.isEmpty()) { "$path: unknown field(s) $unknown - expected one of ${shape.fields.keys}" }
            PortValue.Fields(
                shape.fields.mapValues { (fieldName, fieldShape) ->
                    val fieldJson = obj[fieldName] ?: error("$path: missing field '$fieldName'")
                    decode(fieldShape, fieldJson, "$path.$fieldName")
                }
            )
        }
        is PortShape.Vector -> {
            val arr = requireJsonArray(json, path)
            check(arr.size == shape.size) { "$path: expected ${shape.size} element(s), got ${arr.size}" }
            PortValue.Elements(arr.mapIndexed { index, element -> decode(shape.element, element, "$path[$index]") })
        }
    }

    private fun decodeLeaf(width: Int, json: JsonElement, path: String): List<Boolean> {
        val primitive = json as? JsonPrimitive ?: error("$path: expected a boolean or string for a $width-bit port, got $json")

        primitive.booleanOrNull?.let { value ->
            check(width == 1) { "$path: a boolean value is only valid for a 1-bit port, this port is $width bits wide" }
            return listOf(value)
        }

        val text = primitive.content
        val magnitude = when {
            text.startsWith("0x") || text.startsWith("0X") -> BigInteger(text.substring(2), 16)
            text.startsWith("0b") || text.startsWith("0B") -> BigInteger(text.substring(2), 2)
            else -> {
                val bytes = text.toByteArray(Charsets.UTF_8)
                check(bytes.size * 8 == width) {
                    "$path: UTF-8 string \"$text\" is ${bytes.size} byte(s) (${bytes.size * 8} bits), but this port is $width bits wide"
                }
                BigInteger(1, bytes)
            }
        }
        return unsignedBigIntegerToBits(magnitude, width)
    }

    /** Renders a PortValue as hex (or nested hex) for mismatch reporting - robust across arbitrary
     *  widths and non-UTF8-safe content, regardless of what encoding the expected value used. */
    fun toDisplayString(value: PortValue): String = when (value) {
        is PortValue.Bits -> "0x" + bitsToUnsignedBigInteger(value.bits).toString(16).padStart((value.bits.size + 3) / 4, '0')
        is PortValue.Fields -> "{" + value.fields.entries.joinToString(", ") { (k, v) -> "$k=${toDisplayString(v)}" } + "}"
        is PortValue.Elements -> "[" + value.elements.joinToString(", ") { toDisplayString(it) } + "]"
    }

    private fun requireJsonObject(json: JsonElement, path: String): JsonObject =
        (json as? JsonObject) ?: error("$path: expected a JSON object, got $json")

    private fun requireJsonArray(json: JsonElement, path: String): JsonArray =
        (json as? JsonArray) ?: error("$path: expected a JSON array, got $json")
}
