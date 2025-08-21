package com.uabutler.cst.node.util

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTTemporary
import com.uabutler.cst.node.expression.CSTExpression

data class CSTGenericInterfaceDefinition(val declaredIdentifier: String): CSTPersistent

data class CSTGenericInterfaceDefinitions(val definitions: List<CSTGenericInterfaceDefinition>): CSTTemporary


data class CSTGenericParameterDefinitionTypeInterfaceList(val interfaceTypes: List<CSTExpression>): CSTTemporary

sealed interface CSTGenericParameterDefinitionType: CSTPersistent

data class CSTNamedGenericParameterDefinitionType(val name: String): CSTGenericParameterDefinitionType

data class CSTFunctionGenericParameterDefinitionType(
    val inputs: List<CSTExpression>,
    val outputs: List<CSTExpression>,
): CSTGenericParameterDefinitionType

data class CSTGenericParameterDefinition(val declaredIdentifier: String, val type: CSTGenericParameterDefinitionType): CSTPersistent

data class CSTGenericParameterDefinitions(val definitions: List<CSTGenericParameterDefinition>): CSTTemporary