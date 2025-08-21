package com.uabutler.resolver.scope

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.cst.node.CSTPersistent

interface Scope {

    val parentScope: Scope?

    fun resolveLocal(name: String): CSTPersistent?

    fun resolveGlobal(name: String): CSTPersistent? {
        return resolveLocal(name) ?: parentScope?.resolveGlobal(name)
    }

    fun resolve(name: String): CSTPersistent {
        return try {
            resolveGlobal(name)!!
        } catch (e: NullPointerException) {
            throw Exception("Unresolved symbol $name", e)
        }
    }

    fun symbols(): List<String>

    fun validateSymbols() = validateSymbols(symbols())

    companion object {
        fun validateSymbols(symbols: List<String>) {
            val symbolSet = mutableSetOf<String>()
            symbols.forEach { symbol ->
                if (symbolSet.contains(symbol)) {
                    throw Exception("Redeclaration of $symbol")
                }

                symbolSet.add(symbol)
            }
        }

        fun String.toIdentifier() = IdentifierNode(this)
    }

}