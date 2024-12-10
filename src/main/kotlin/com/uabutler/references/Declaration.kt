package com.uabutler.references

import com.uabutler.ast.GAPLNode
import com.uabutler.ast.GenericInterfaceDefinitionNode
import com.uabutler.ast.GenericParameterDefinitionNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.functions.FunctionIONode
import com.uabutler.ast.functions.circuits.DeclaredNodeCircuitExpressionNode
import com.uabutler.ast.interfaces.InterfaceDefinitionNode
import com.uabutler.ast.interfaces.RecordInterfacePortNode

interface Declaration {
    val identifier: String
    val astNode: GAPLNode
}

data class GenericInterfaceDeclaration(
    override val identifier: String,
    override val astNode: GenericInterfaceDefinitionNode,
): Declaration

data class GenericParameterDeclaration(
    override val identifier: String,
    override val astNode: GenericParameterDefinitionNode,
): Declaration

data class FunctionIODeclaration(
    override val identifier: String,
    override val astNode: FunctionIONode,
): Declaration

data class FunctionDeclaration(
    override val identifier: String,
    override val astNode: FunctionDefinitionNode,
): Declaration

data class InterfaceDeclaration(
    override val identifier: String,
    override val astNode: InterfaceDefinitionNode,
): Declaration

data class PortDeclaration(
    override val identifier: String,
    override val astNode: RecordInterfacePortNode,
): Declaration

data class NodeDeclaration(
    override val identifier: String,
    override val astNode: DeclaredNodeCircuitExpressionNode,
): Declaration
