package com.uabutler.ast.functions

import com.uabutler.ast.GenericInterfaceDefinitionNode
import com.uabutler.ast.GenericParameterDefinitionNode
import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.functions.circuits.CircuitStatementNode

data class FunctionDefinitionNode(
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
    val inputFunctionIO: List<FunctionIONode>,
    val outputFunctionIO: List<FunctionIONode>,
    val statements: List<CircuitStatementNode>,
): PersistentNode