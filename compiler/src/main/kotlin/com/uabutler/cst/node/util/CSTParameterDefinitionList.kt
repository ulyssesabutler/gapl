package com.uabutler.cst.node.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression

data class CSTParameterDefinitionTypeInterfaceList(val interfaceTypes: List<CSTExpression>): CSTTemporary

sealed interface CSTParameterDefinitionType: CSTPersistent

data object CSTIntegerParameterDefinitionType: CSTParameterDefinitionType

data object CSTInterfaceParameterDefinitionType: CSTParameterDefinitionType

data class CSTFunctionParameterDefinitionType(
    val inputs: List<CSTExpression>,
    val outputs: List<CSTExpression>,
): CSTParameterDefinitionType

data class CSTParameterDefinition(val declaredIdentifier: String, val type: CSTParameterDefinitionType): CSTPersistent

data class CSTParameterDefinitionList(val definitions: List<CSTParameterDefinition>): CSTTemporary