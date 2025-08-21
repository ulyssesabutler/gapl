package com.uabutler.cst.node

import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition

data class CSTProgram(
    val interfaceDefinitions: List<CSTInterfaceDefinition>,
    val functionDefinitions: List<CSTFunctionDefinition>,
): CSTPersistent