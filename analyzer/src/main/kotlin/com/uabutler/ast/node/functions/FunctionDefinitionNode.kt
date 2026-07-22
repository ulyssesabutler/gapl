package com.uabutler.ast.node.functions

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.GenericInterfaceDefinitionNode
import com.uabutler.ast.node.GenericParameterDefinitionNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.functions.circuits.CircuitStatementNode
import com.uabutler.diagnostics.SourceSpan

data class FunctionDefinitionNode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val genericInterfaces: List<GenericInterfaceDefinitionNode>,
    val genericParameters: List<GenericParameterDefinitionNode>,
    val inputFunctionIO: List<FunctionIONode>,
    val outputFunctionIO: List<FunctionIONode>,
    val statements: List<CircuitStatementNode>,
): GAPLNode
