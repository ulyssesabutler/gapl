package com.uabutler.netlistir.util

/**
 * Utility class providing helper methods for toString, equals, and hashCode implementations
 * that properly handle circular references.
 */
object ObjectUtils {
    /**
     * Generate a toString representation for an object, with separate handling for normal and identity properties.
     * 
     * @param obj The object instance
     * @param normalProps Map of regular property names to their values
     * @param identityProps Map of identity property names to their values (shown as ClassName@hash)
     * @return A string representation of the object
     */
    fun toStringBuilder(
        obj: Any,
        normalProps: Map<String, Any?> = emptyMap(),
        identityProps: Map<String, Any?> = emptyMap()
    ): String {
        val className = obj::class.java.simpleName
        val sb = StringBuilder(className)
        sb.append("@${System.identityHashCode(obj).toString(16)}")
        sb.append("(")

        val normalPropStrings = normalProps.map { (name, value) ->
            val valueStr = when (value) {
                is Map<*, *> -> mapToString(value)
                else -> value.toString()
            }
            "$name=$valueStr"
        }

        val identityPropStrings = identityProps.map { (name, value) ->
            val valueStr = if (value != null) {
                "${value::class.java.simpleName}@${System.identityHashCode(value).toString(16)}"
            } else {
                "null"
            }
            "$name=$valueStr"
        }

        val allProps = normalPropStrings + identityPropStrings
        sb.append(allProps.joinToString(", "))
        sb.append(")")
        return sb.toString()
    }

    /**
     * Helper method to convert a map to string without triggering circular references
     */
    private fun mapToString(map: Map<*, *>): String {
        return map.entries.joinToString(
            prefix = "{", 
            postfix = "}", 
            transform = { "${it.key}=${it.value}" }
        )
    }
}
