package com.uabutler.netlistir.builder.util

object Range {
    fun from(
        dimensions: List<Int>,
        indices: List<Int>,
        range: IntRange?,
    ): IntRange {
        // Written at 3am by brute force. I don't know why, how, or even if it works.
        val lowerDimensionSize = dimensions.drop(1).fold(1) { acc, i -> acc * i }
        if (dimensions.isEmpty() || indices.isEmpty()) return if (range != null) (range.first * lowerDimensionSize) until ((range.last + 1) * lowerDimensionSize) else 0 until dimensions.fold(1) { acc, i -> acc * i }

        val startOfCurrentDimension = indices.first() * lowerDimensionSize
        val lowerDimensionRange = from(dimensions.drop(1), indices.drop(1), range)

        return (lowerDimensionRange.first + startOfCurrentDimension)..(lowerDimensionRange.last + startOfCurrentDimension)
    }
}