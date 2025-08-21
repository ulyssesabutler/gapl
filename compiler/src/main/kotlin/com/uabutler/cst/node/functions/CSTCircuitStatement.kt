package com.uabutler.cst.node.functions

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTCircuitExpression
import com.uabutler.cst.node.expression.CSTExpression

sealed interface CSTCircuitStatement: CSTPersistent

data class CSTConditionalCircuitBody(val circuitStatements: List<CSTCircuitStatement>): CSTTemporary

data class CSTConditional(
    val predicate: CSTExpression,
    val ifBody: List<CSTCircuitStatement>,
    val elseBody: List<CSTCircuitStatement>,
): CSTTemporary

data class CSTConditionalCircuitStatement(
    val predicate: CSTExpression,
    val ifBody: List<CSTCircuitStatement>,
    val elseBody: List<CSTCircuitStatement>,
): CSTCircuitStatement

data class CSTNonConditionalCircuitStatement(val statement: CSTCircuitExpression): CSTCircuitStatement