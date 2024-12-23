package com.uabutler.references

import com.uabutler.ast.EmptyNode
import com.uabutler.ast.GAPLNode
import com.uabutler.ast.ProgramNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.functions.circuits.RecordInterfaceConstructorExpressionNode
import com.uabutler.ast.interfaces.RecordInterfaceDefinitionNode

interface Scope {

    val parent: Scope?
    val astNode: GAPLNode

    fun declarations(): Collection<Declaration>
    fun children(): Collection<Scope>

    fun allDeclarations(): Collection<Declaration> = declarations() + (parent?.allDeclarations() ?: emptyList())

    fun getDeclaration(identifier: String): Declaration? = allDeclarations().firstOrNull { it.identifier == identifier }

}

data class ProgramScope(
    val interfaceDeclarations: Collection<InterfaceDeclaration>,
    val functionDeclarations: Collection<FunctionDeclaration>,
    override val astNode: ProgramNode,
): Scope {

    init { astNode.associatedScope = this }

    val interfaceScopes: MutableCollection<InterfaceScope> = mutableSetOf()
    val functionScopes: MutableCollection<FunctionScope> = mutableSetOf()

    override val parent = null
    override fun declarations() = interfaceDeclarations + functionDeclarations
    override fun children() = interfaceScopes + functionScopes

    fun addInterfaceScope(scope: InterfaceScope) = interfaceScopes.add(scope)
    fun addFunctionScope(scope: FunctionScope) = functionScopes.add(scope)

}


interface GenericScope: Scope {

    val genericInterfaces: Collection<GenericInterfaceDeclaration>
    val genericParameters: Collection<GenericParameterDeclaration>

}


data class InterfaceScope(
    override val genericInterfaces: Collection<GenericInterfaceDeclaration>,
    override val genericParameters: Collection<GenericParameterDeclaration>,
    val ports: Collection<PortDeclaration>,
    val program: ProgramScope,
    override val astNode: RecordInterfaceDefinitionNode,
): GenericScope {

    init { astNode.associatedScope = this }

    override val parent = program
    override fun declarations() = genericInterfaces + genericParameters + ports
    override fun children() = emptyList<Scope>()

}


interface ConnectionScope: Scope {

    val nodes: Collection<NodeDeclaration>

    val interfaceConstructors: MutableCollection<InterfaceConstructorScope>
    val ifBodies: MutableCollection<IfBodyScope>

    override fun children() = interfaceConstructors + ifBodies

    fun addInterfaceConstructor(scope: InterfaceConstructorScope) = interfaceConstructors.add(scope)
    fun addIfBody(scope: IfBodyScope) = ifBodies.add(scope)

}

data class FunctionScope(
    override val genericInterfaces: Collection<GenericInterfaceDeclaration>,
    override val genericParameters: Collection<GenericParameterDeclaration>,
    val inputs: Collection<FunctionIODeclaration>,
    val outputs: Collection<FunctionIODeclaration>,
    override val nodes: Collection<NodeDeclaration>,
    val program: ProgramScope,
    override val astNode: FunctionDefinitionNode,
): GenericScope, ConnectionScope {

    init { astNode.associatedScope = this }

    override fun declarations() = genericInterfaces + genericParameters + inputs + outputs + nodes

    override val interfaceConstructors = mutableListOf<InterfaceConstructorScope>()
    override val ifBodies = mutableListOf<IfBodyScope>()
    override val parent = program

}

data class InterfaceConstructorScope(
    override val nodes: Collection<NodeDeclaration>,
    override val parent: Scope,
    override val astNode: RecordInterfaceConstructorExpressionNode,
): ConnectionScope {

    init { astNode.associatedScope = this }

    override fun declarations() = nodes

    override val interfaceConstructors = mutableListOf<InterfaceConstructorScope>()
    override val ifBodies = mutableListOf<IfBodyScope>()

}

data class IfBodyScope(
    override val nodes: Collection<NodeDeclaration>,
    override val parent: Scope,
): ConnectionScope {

    // TODO: We'll have to rework the AST a bit...
    override val astNode = EmptyNode

    override fun declarations() = nodes

    override val interfaceConstructors = mutableListOf<InterfaceConstructorScope>()
    override val ifBodies = mutableListOf<IfBodyScope>()

}