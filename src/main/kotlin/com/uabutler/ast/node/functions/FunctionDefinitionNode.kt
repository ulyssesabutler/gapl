package com.uabutler.ast.node.functions

import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.PersistentNode
import com.uabutler.ast.node.functions.circuits.CircuitStatementNode

data class FunctionDefinitionNode(
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
    val inputFunctionIO: List<FunctionIONode>,
    val outputFunctionIO: List<FunctionIONode>,
    val statements: List<CircuitStatementNode>,
): PersistentNode