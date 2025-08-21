package com.uabutler.cst.node.expression.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTExpression

sealed interface CSTCircuitExpressionType: CSTPersistent

sealed interface CSTTransformerType: CSTPersistent

object CSTInTransformerType: CSTTransformerType
object CSTOutTransformerType: CSTTransformerType
object CSTInoutTransformerType: CSTTransformerType

data class CSTBasicCircuitExpressionType(
    val nodeType: CSTExpression,
): CSTCircuitExpressionType

data class CSTTransformerCircuitExpressionType(
    val transformer: CSTTransformer,
    val transformerType: CSTTransformerType,
    val nodeType: CSTExpression,
): CSTCircuitExpressionType