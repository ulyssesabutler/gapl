package com.uabutler.netlistir.builder.util

object Range {
    fun from(
        dimensions: List<Int>,
        indices: List<Int>,
        range: IntRange?,
    ): IntRange {

        var currentIndex = 0

        indices.forEachIndexed { index, it ->
            val previousDimension = dimensions.getOrNull(index - 1) ?: 1
            currentIndex = (currentIndex * previousDimension) + it
        }

        var startIndex = currentIndex
        var endIndex = currentIndex

        val previousDimension = dimensions.getOrNull(indices.size - 1) ?: 1

        if (range != null) {
            startIndex = (startIndex * previousDimension) + range.first
            endIndex = (endIndex * previousDimension) + range.last
        }

        val dimensionsProcessed = indices.size + (if (range != null) 1 else 0)
        val size = dimensions.drop(dimensionsProcessed).fold(1) { acc, i -> acc * i }

        startIndex *= size
        endIndex = (endIndex + 1) * size - 1

        return startIndex..endIndex
    }
}