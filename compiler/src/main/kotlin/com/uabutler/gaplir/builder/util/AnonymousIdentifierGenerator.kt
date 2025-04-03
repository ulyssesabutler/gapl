package com.uabutler.gaplir.builder.util

object AnonymousIdentifierGenerator {
    private var counter = 0

    fun genIdentifier() = "anonymous_${++counter}"
}
