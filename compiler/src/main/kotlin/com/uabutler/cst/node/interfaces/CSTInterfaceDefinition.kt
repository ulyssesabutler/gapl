package com.uabutler.cst.node.interfaces

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.util.CSTParameterDefinition

sealed class CSTInterfaceDefinition(
    open val declaredIdentifier: String,
    open val parameterDefinitions: List<CSTParameterDefinition>,
): CSTPersistent

data class CSTAliasInterfaceDefinition(
    override val declaredIdentifier: String,
    override val parameterDefinitions: List<CSTParameterDefinition>,
    val aliasedInterface: CSTExpression,
): CSTInterfaceDefinition(declaredIdentifier, parameterDefinitions)

data class CSTPortDefinition(
    val declaredIdentifier: String,
    val interfaceType: CSTExpression,
): CSTPersistent

data class CSTRecordInterfaceDefinition(
    override val declaredIdentifier: String,
    override val parameterDefinitions: List<CSTParameterDefinition>,
    val ports: List<CSTPortDefinition>,
): CSTInterfaceDefinition(declaredIdentifier, parameterDefinitions)