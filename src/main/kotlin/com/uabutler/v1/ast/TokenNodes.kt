package com.uabutler.v1.ast

data class IdentifierNode(
    val value: String,
): PersistentNode {
    override var parent: PersistentNode? = null
}

data class IntegerLiteralNode(
    val value: Int,
): PersistentNode {
    override var parent: PersistentNode? = null
}