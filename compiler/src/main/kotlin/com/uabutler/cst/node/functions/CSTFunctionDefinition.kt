package com.uabutler.cst.node.functions

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.util.CSTParameterDefinition

data class CSTFunctionIO(
    val declaredIdentifier: String,
    val interfaceType: CSTExpression
): CSTPersistent

data class CSTFunctionIOList(val ioList: List<CSTFunctionIO>): CSTTemporary

data class CSTFunctionDefinition(
    val declaredIdentifier: String,
    val parameterDefinitions: List<CSTParameterDefinition>,
    val inputs: List<CSTFunctionIO>,
    val outputs: List<CSTFunctionIO>,
    val statements: List<CSTCircuitStatement>,
): CSTPersistent