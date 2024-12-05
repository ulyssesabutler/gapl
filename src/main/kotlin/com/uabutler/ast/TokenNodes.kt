package com.uabutler.ast

data class IdentifierNode(val value: String): PersistentNode()
data class IntegerLiteralNode(val value: Int): PersistentNode()