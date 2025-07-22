package com.uabutler.netlistir.util

import kotlin.reflect.KProperty1

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
     * Generate a toString representation for an object using property references.
     * 
     * @param obj The object instance
     * @param normalProps List of regular property references
     * @param identityProps List of identity property references
     * @return A string representation of the object
     */
    inline fun <reified T : Any> toStringBuilder(
        obj: T,
        normalProps: List<KProperty1<T, *>> = emptyList(),
        identityProps: List<KProperty1<T, *>> = emptyList()
    ): String {
        val normalPropsMap = normalProps.associate { prop -> 
            prop.name to prop.get(obj) 
        }

        val identityPropsMap = identityProps.associate { prop -> 
            prop.name to prop.get(obj) 
        }

        return toStringBuilder(
            obj = obj,
            normalProps = normalPropsMap,
            identityProps = identityPropsMap
        )
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

    /**
     * Generate a hashCode value for an object, excluding properties that would cause circular references.
     * 
     * @param properties Values of properties to include in hashCode calculation
     * @return A hashCode value
     */
    fun hashCodeBuilder(vararg properties: Any?): Int {
        return properties.fold(0) { acc, prop ->
            31 * acc + (prop?.hashCode() ?: 0)
        }
    }

    /**
     * Compares two objects for equality, handling parent references by comparing only their identifiers.
     * 
     * @param self The current object instance
     * @param other The object to compare with
     * @param propertyComparisons List of property comparisons to perform
     * @return true if all comparisons pass, false otherwise
     */
    inline fun <reified T : Any> equalsBuilder(
        self: Any,
        other: Any?,
        vararg propertyComparisons: (T) -> Boolean
    ): Boolean {
        if (self === other) return true
        if (other !is T) return false

        return propertyComparisons.all { it(other) }
    }

    /**
     * Creates a function to safely compare parent properties by their identifier property
     * 
     * @param selfValue The property from this object
     * @param otherValue The property from the other object
     * @param identifierAccessor Function to access the identifier property
     * @return true if both identifiers match, false otherwise
     */
    inline fun <T : Any> identifierEquals(
        selfValue: T?, 
        otherValue: T?, 
        crossinline identifierAccessor: (T) -> Any?
    ): Boolean {
        if (selfValue === otherValue) return true
        if (selfValue == null || otherValue == null) return selfValue == otherValue

        return identifierAccessor(selfValue) == identifierAccessor(otherValue)
    }
}
