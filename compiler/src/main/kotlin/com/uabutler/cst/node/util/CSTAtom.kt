package com.uabutler.cst.node.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTExpression

data class CSTAtom(
    val identifier: String,
    val parameterValues: List<CSTExpression>,
): CSTPersistent