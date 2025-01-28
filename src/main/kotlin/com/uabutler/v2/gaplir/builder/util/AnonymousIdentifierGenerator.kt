package com.uabutler.v2.gaplir.builder.util

object AnonymousIdentifierGenerator {
    private var counter = 0

    fun genIdentifier() = "anonymous-${++counter}"
}