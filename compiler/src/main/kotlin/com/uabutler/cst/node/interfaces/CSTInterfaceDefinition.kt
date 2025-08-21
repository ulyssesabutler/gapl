package com.uabutler.cst.node.interfaces

import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTExpression
import com.uabutler.cst.node.util.CSTGenericInterfaceDefinition
import com.uabutler.cst.node.util.CSTGenericParameterDefinition

sealed class CSTInterfaceDefinition(
    open val declaredIdentifier: String,
    open val interfaceDefinitions: List<CSTGenericInterfaceDefinition>,
    open val parameterDefinitions: List<CSTGenericParameterDefinition>,
): CSTPersistent

data class CSTAliasInterfaceDefinition(
    override val declaredIdentifier: String,
    override val interfaceDefinitions: List<CSTGenericInterfaceDefinition>,
    override val parameterDefinitions: List<CSTGenericParameterDefinition>,
    val aliasedInterface: CSTExpression,
): CSTInterfaceDefinition(declaredIdentifier, interfaceDefinitions, parameterDefinitions)

data class CSTPortDefinition(
    val declaredIdentifier: String,
    val interfaceType: CSTExpression,
): CSTPersistent

data class CSTRecordInterfaceDefinition(
    override val declaredIdentifier: String,
    override val interfaceDefinitions: List<CSTGenericInterfaceDefinition>,
    override val parameterDefinitions: List<CSTGenericParameterDefinition>,
    val ports: List<CSTPortDefinition>,
): CSTInterfaceDefinition(declaredIdentifier, interfaceDefinitions, parameterDefinitions)