package com.uabutler.util

object Range {
    fun from(
        dimensions: List<Int>,
        indices: List<Int>,
        range: IntRange?,
    ): IntRange {
        // Calculate the size of each dimension's stride in the flattened array
        val strides = mutableListOf<Int>()
        var currentStride = 1
        for (i in dimensions.size - 1 downTo 0) {
            strides.add(0, currentStride)
            currentStride *= dimensions[i]
        }

        // Calculate base offset using provided indices
        var baseOffset = 0
        for (i in indices.indices) {
            baseOffset += indices[i] * strides[i]
        }

        // If we have fewer indices than dimensions, we need to calculate the size of the subarray
        val subarraySize = if (indices.size < dimensions.size) {
            var size = 1
            for (i in indices.size until dimensions.size) {
                size *= dimensions[i]
            }
            size
        } else {
            1
        }

        // If range is provided, apply it to the last dimension
        // Otherwise, take the full subarray
        val startOffset = if (range != null && indices.size < dimensions.size) {
            val lastDimensionStride = strides[indices.size]
            baseOffset + range.first * lastDimensionStride
        } else {
            baseOffset
        }

        val endOffset = if (range != null && indices.size < dimensions.size) {
            val rangeSize = range.last - range.first + 1
            val lastDimensionStride = strides[indices.size]
            val lastDimensionSize = dimensions[indices.size]

            // Calculate the size of the remaining dimensions
            var remainingSize = 1
            for (i in indices.size + 1 until dimensions.size) {
                remainingSize *= dimensions[i]
            }

            baseOffset + (range.first * lastDimensionStride) + (rangeSize * lastDimensionStride * remainingSize) - 1
        } else {
            baseOffset + subarraySize - 1
        }

        println()
        println("Dimensions: $dimensions")
        println("Indices: $indices")
        println("Range: $range")
        println("Start offset: $startOffset")
        println("End offset: $endOffset")

        return startOffset..endOffset
    }
}