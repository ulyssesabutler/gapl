package com.uabutler.v1.util

import kotlin.random.Random

class AnonymousIdentifiers {

    private companion object {
        fun randomCharacter() = Random.nextInt('a'.code, 'z'.code).toChar()
        fun randomString(length: Int = 6) = "anonymous_" + String(CharArray(length) { randomCharacter() })
    }

    private val identifiers = mutableSetOf<String>()

    fun generateIdentifier(): String {
        var proposal = randomString()
        while (proposal in identifiers) { proposal = randomString() }
        return proposal
    }

    fun getIdentifiers() = identifiers.toSet()

}