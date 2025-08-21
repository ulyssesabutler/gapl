package com.uabutler.cst.node.expression

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.util.CSTCircuitExpressionType

data class CSTCircuitExpression(
    val connectedGroups: List<CSTCircuitGroupExpression>,
): CSTPersistent

data class CSTCircuitGroupExpression(
    val groupedNodes: List<CSTCircuitNodeExpression>,
): CSTPersistent

sealed interface CSTCircuitNodeExpression: CSTPersistent

data class CSTLoneCircuitNodeExpression(val expression: CSTExpression): CSTCircuitNodeExpression

data class CSTDeclaredCircuitNodeExpression(
    val declaredIdentifier: String,
    val type: CSTCircuitExpressionType,
): CSTCircuitNodeExpression

data class CSTParenthesesCircuitNodeExpression(val expression: CSTCircuitExpression): CSTCircuitNodeExpression
