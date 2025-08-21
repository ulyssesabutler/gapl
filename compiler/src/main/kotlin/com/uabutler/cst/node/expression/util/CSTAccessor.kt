package com.uabutler.cst.node.expression.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTExpression

sealed interface CSTAccessor: CSTPersistent

data class CSTVectorItemAccessor(
    val index: CSTExpression,
): CSTAccessor

data class CSTVectorSliceAccessor(
    val start: CSTExpression,
    val end: CSTExpression,
): CSTAccessor

data class CSTMemberAccessor(
    val portIdentifier: String,
): CSTAccessor