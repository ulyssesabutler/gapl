package com.uabutler.cst.node.util

import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression

data class CSTParameterValues(val parameterValues: List<CSTExpression>): CSTTemporary
