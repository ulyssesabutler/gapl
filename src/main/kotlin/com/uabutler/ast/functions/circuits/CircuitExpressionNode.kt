package com.uabutler.ast.functions.circuits

import com.uabutler.ast.IdentifierNode
import com.uabutler.ast.PersistentNode
import com.uabutler.ast.ScopeNode
import com.uabutler.ast.interfaces.InterfaceExpressionNode
import com.uabutler.references.InterfaceConstructorScope
import com.uabutler.references.Scope

sealed interface CircuitExpressionNode: PersistentNode

data class CircuitConnectionExpressionNode(
    val connectedExpression: List<CircuitGroupExpressionNode>,
): CircuitExpressionNode {
    override var parent: PersistentNode? = null
}

data class CircuitGroupExpressionNode(
    val expressions: List<CircuitNodeExpressionNode>,
): PersistentNode {
    override var parent: PersistentNode? = null
}

sealed interface CircuitNodeExpressionNode: PersistentNode

data class IdentifierCircuitExpressionNode(
    val identifier: IdentifierNode,
): CircuitNodeExpressionNode {
    override var parent: PersistentNode? = null
}

data class AnonymousNodeCircuitExpressionNode(
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode {
    override var parent: PersistentNode? = null
}

data class DeclaredNodeCircuitExpressionNode(
    val identifier: IdentifierNode,
    val type: InterfaceExpressionNode,
): CircuitNodeExpressionNode {
    override var parent: PersistentNode? = null
}

data class ReferenceCircuitExpressionNode(
    val identifier: IdentifierNode,
    val singleAccesses: List<SingleAccessOperationNode>,
    val multipleAccess: MultipleAccessOperationNode?,
): CircuitNodeExpressionNode {
    override var parent: PersistentNode? = null
}

data class RecordInterfaceConstructorExpressionNode(
    val statements: List<CircuitStatementNode>,
): CircuitNodeExpressionNode, ScopeNode {
    override var parent: PersistentNode? = null
    override var associatedScope: Scope? = null
    fun interfaceConstructorScope() = associatedScope?.let { if (it is InterfaceConstructorScope) it else null }
}

data class CircuitExpressionNodeCircuitExpression(
    val expression: CircuitExpressionNode,
): CircuitNodeExpressionNode {
    override var parent: PersistentNode? = null
}