package com.uabutler.ast.node

import java.math.BigInteger

data class IdentifierNode(
    val value: String,
): PersistentNode

data class IntegerLiteralNode(
    val value: BigInteger,
): PersistentNode