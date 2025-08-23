package com.uabutler.resolver.scope

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.util.PredefinedFunctionNames

interface Scope {

    val parentScope: Scope?

    fun resolveLocal(name: String): CSTPersistent?

    fun resolveGlobal(name: String): CSTPersistent? {
        return resolveLocal(name) ?: parentScope?.resolveGlobal(name)
    }

    fun resolve(name: String): CSTPersistent {
        if (name in predefinedFunctionNames) {
            return CSTFunctionDefinition(
                declaredIdentifier = name,
                parameterDefinitions = emptyList(),
                inputs = emptyList(),
                outputs = emptyList(),
                statements = emptyList(),
            )
        }

        return try {
            resolveGlobal(name)!!
        } catch (e: NullPointerException) {
            throw Exception("Unresolved symbol $name", e)
        }
    }

    fun symbols(): List<String>

    fun validateSymbols() = validateSymbols(symbols())

    companion object {
        private val predefinedFunctionNames = PredefinedFunctionNames.entries.map { it.gaplName }.toSet()

        fun validateSymbols(symbols: List<String>) {
            val symbolSet = mutableSetOf<String>()
            symbols.forEach { symbol ->
                if (symbolSet.contains(symbol) || symbol in predefinedFunctionNames) {
                    throw Exception("Redeclaration of $symbol")
                }

                symbolSet.add(symbol)
            }
        }

        fun String.toIdentifier() = IdentifierNode(this)
    }

}