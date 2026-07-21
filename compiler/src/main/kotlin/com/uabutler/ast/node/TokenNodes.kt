package com.uabutler.ast.node

import com.uabutler.diagnostics.SourceSpan
import java.math.BigInteger

data class IdentifierNode(
    override val span: SourceSpan,
    val value: String,
): GAPLNode

data class IntegerLiteralNode(
    override val span: SourceSpan,
    val value: BigInteger,
): GAPLNode
