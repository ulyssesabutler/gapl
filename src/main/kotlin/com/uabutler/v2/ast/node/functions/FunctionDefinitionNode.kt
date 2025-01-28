package com.uabutler.v2.ast.node.functions

import com.uabutler.v2.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.v2.ast.node.GenericParameterDefinitionNode
import com.uabutler.v2.ast.node.IdentifierNode
import com.uabutler.v2.ast.node.PersistentNode
import com.uabutler.v2.ast.node.functions.circuits.CircuitStatementNode

data class FunctionDefinitionNode(
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
    val inputFunctionIO: List<FunctionIONode>,
    val outputFunctionIO: List<FunctionIONode>,
    val statements: List<CircuitStatementNode>,
): PersistentNode