package com.uabutler.ast.node.functions

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.functions.interfaces.InterfaceTypeNode
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
import com.uabutler.diagnostics.SourceSpan

data class AbstractFunctionIONode(
    override val span: SourceSpan,
    val interfaceType: InterfaceTypeNode,
    val interfaceExpression: InterfaceExpressionNode,
): GAPLNode

data class FunctionIONode(
    override val span: SourceSpan,
    val identifier: IdentifierNode,
    val interfaceType: InterfaceTypeNode,
    val interfaceExpression: InterfaceExpressionNode,
): GAPLNode
