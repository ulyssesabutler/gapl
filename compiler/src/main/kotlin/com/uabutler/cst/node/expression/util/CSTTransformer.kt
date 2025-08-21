package com.uabutler.cst.node.expression.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTCircuitExpression
import com.uabutler.cst.node.expression.CSTExpression

data class CSTRecordTransformerEntry(
    val portIdentifier: String,
    val transformation: CSTCircuitExpression,
): CSTPersistent

data class CSTVectorTransformerEntry(
    val index: CSTExpression,
    val transformation: CSTCircuitExpression,
): CSTPersistent

sealed interface CSTTransformer: CSTPersistent

data class CSTRecordTransformer(val entries: List<CSTRecordTransformerEntry>): CSTTransformer

data class CSTVectorTransformer(val entries: List<CSTVectorTransformerEntry>): CSTTransformer
