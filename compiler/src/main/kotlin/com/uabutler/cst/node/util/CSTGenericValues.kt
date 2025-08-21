package com.uabutler.cst.node.util

import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression

sealed interface CSTGenericValues: CSTTemporary

data class CSTGenericInterfaceValues(val interfaceValues: List<CSTExpression>): CSTGenericValues

data class CSTGenericParameterValues(val parameterValues: List<CSTExpression>): CSTGenericValues
