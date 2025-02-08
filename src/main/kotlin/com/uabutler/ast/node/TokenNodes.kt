package com.uabutler.ast.node

data class IdentifierNode(
    val value: String,
): PersistentNode

data class IntegerLiteralNode(
    val value: Int,
): PersistentNode