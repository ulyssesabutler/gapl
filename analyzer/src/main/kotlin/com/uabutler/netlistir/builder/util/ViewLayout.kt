package com.uabutler.netlistir.builder.util

import java.math.BigInteger

/**
 * What a view lowers to: an ordinary record of its fields, plus where each of that record's bits
 * comes from in the base.
 *
 * Bits are numbered in flattened-wire order - the order `WireVectorGroup.wires()` lists them for a
 * group of that structure (InterfaceFlattener's wire vectors in turn, each row-major). So `baseBits[i]`
 * is the base bit that view bit `i` names.
 */
data class ViewLayout(
    val base: InterfaceStructure,
    val structure: RecordInterfaceStructure,
    val baseBits: List<Int>,
) {
    companion object {
        /**
         * The bits of [base] a path selects, in the order a fresh group of the selected structure would
         * list them. Mirrors `WireVector.projection`: the same member-prefix match and `Range.from`.
         */
        fun selectedBits(
            base: InterfaceStructure,
            members: List<String>,
            indices: List<Int>,
            range: IntRange?,
        ): List<Int> {
            var offset = 0
            return InterfaceFlattener.fromInterfaceStructure(base).flatMap { vector ->
                val start = offset
                offset += vector.width
                val matches = vector.identifier.size >= members.size && vector.identifier.subList(0, members.size) == members
                if (matches) Range.from(vector.dimensions, indices, range).map { start + it } else emptyList()
            }
        }

        /*
         * project/overlay carry their view's bit mapping as ordinary integer parameters, so that the
         * mapping survives anywhere a Module.Invocation goes (including being passed to a higher-order
         * function), and so that two same-shaped views at different offsets never compare equal as
         * invocations. Encoded as (first base bit, length) runs, since views are mostly a few
         * contiguous slices.
         */

        fun encode(baseBits: List<Int>): List<IntegerParameterValue> {
            val runs = mutableListOf<Pair<Int, Int>>()
            baseBits.forEach { bit ->
                val last = runs.lastOrNull()
                if (last != null && last.first + last.second == bit) {
                    runs[runs.size - 1] = last.first to last.second + 1
                } else {
                    runs += bit to 1
                }
            }
            return runs.flatMap { (start, length) ->
                listOf(IntegerParameterValue(BigInteger.valueOf(start.toLong())), IntegerParameterValue(BigInteger.valueOf(length.toLong())))
            }
        }

        fun decode(parameters: List<ParameterValue<*>>): List<Int> {
            return parameters
                .map { (it as IntegerParameterValue).value.intValueExact() }
                .chunked(2)
                .flatMap { (start, length) -> start until start + length }
        }
    }
}
