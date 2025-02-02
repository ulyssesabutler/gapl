package com.uabutler.util

import kotlin.reflect.KProperty1

class Test {
    val a: Int = 0
    val b: Int = 1
}

object StringGenerator {

    fun <T: Any> genToStringFromProperties(instance: T, vararg properties: KProperty1<T, *>): String {
        val className = instance::class.java.simpleName
        val propertyValues = properties.associate { it.name to it.get(instance) as Any }
        return genToStringFromValues(className, instance.hashCode(), propertyValues)
    }

    fun genToStringFromValues(instanceName: String, hashCode: Int, values: Map<String, Any>): String {
        return buildString {
            val valuesString = values
                .map { (propertyName, propertyValue) -> "$propertyName=${genToStringHelper(propertyValue)}" }
                .joinToString(separator = ",\n")

            if (valuesString.contains("\n")) {
                appendLine("$instanceName@${hashCode.toString(16)}(")
                appendLine(valuesString.prependIndent())
                append(")")
            } else {
                append("$instanceName@${hashCode.toString(16)}(")
                append(valuesString)
                append(")")
            }
        }
    }

    private fun genToStringHelper(value: Any): String {
        return if (value is Iterable<*>) listToString(value) else value.toString()
    }

    fun listToString(list: Iterable<*>): String {
        return buildString {
            val valuesString = list.joinToString(separator = ",\n")

            if (valuesString.contains("\n")) {
                appendLine("[")
                appendLine(valuesString.prependIndent())
                append("]")
            } else {
                append("[")
                append(valuesString)
                append("]")
            }
        }
    }

}