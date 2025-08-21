package com.uabutler.cst.node.expression

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.util.CSTAccessor
import com.uabutler.cst.node.util.CSTAtom

sealed interface CSTExpression: CSTPersistent

data class CSTAtomExpression(val atom: CSTAtom): CSTExpression

data object CSTWireExpression: CSTExpression

data object CSTTrueExpression: CSTExpression

data object CSTFalseExpression: CSTExpression

data class CSTIntLiteralExpression(val value: Int): CSTExpression

data class CSTAccessorExpression(
    val accessed: CSTExpression,
    val accessor: CSTAccessor,
): CSTExpression

data class CSTParenthesizedExpression(val expression: CSTExpression): CSTExpression

data class CSTMultiplicationExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTDivisionExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTAdditionExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTSubtractionExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTLessThanExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTGreaterThanExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTLessThanOrEqualsExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTGreaterThanOrEqualsExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTEqualsExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTNotEqualsExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTLogicalAndExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression

data class CSTLogicalOrExpression(val lhs: CSTExpression, val rhs: CSTExpression): CSTExpression
