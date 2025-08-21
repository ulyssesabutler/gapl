package com.uabutler.cst.node.functions

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinition
import com.uabutler.cst.node.util.CSTGenericParameterDefinition

data class CSTFunctionIO(
    val declaredIdentifier: String,
    val interfaceType: CSTExpression
): CSTPersistent

data class CSTFunctionIOList(val ioList: List<CSTFunctionIO>): CSTTemporary

data class CSTFunctionDefinition(
    val declaredIdentifier: String,
    val interfaceDefinitions: List<CSTGenericInterfaceDefinition>,
    val parameterDefinitions: List<CSTGenericParameterDefinition>,
    val inputs: List<CSTFunctionIO>,
    val outputs: List<CSTFunctionIO>,
    val statements: List<CSTCircuitStatement>,
): CSTPersistent