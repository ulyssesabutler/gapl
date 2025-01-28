package com.uabutler.v2.ast.node

data class IdentifierNode(
    val value: String,
): PersistentNode

data class IntegerLiteralNode(
    val value: Int,
): PersistentNode