package com.uabutler.resolver.scope

import com.uabutler.ast.node.ProgramNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.CSTProgram
import com.uabutler.resolver.scope.functions.FunctionDefinitionScope
import com.uabutler.resolver.scope.interfaces.InterfaceDefinitionScope

class ProgramScope(val program: CSTProgram): Scope {

    override val parentScope: Scope? = null

    private val interfaces = program.interfaceDefinitions.associateBy { it.declaredIdentifier }
    private val functions = program.functionDefinitions.associateBy { it.declaredIdentifier }

    private val localSymbolTable = interfaces + functions

    override fun resolveLocal(name: String): CSTPersistent? {
        return localSymbolTable[name]
    }

    override fun resolveGlobal(name: String): CSTPersistent? {
        return resolveLocal(name)
    }

    override fun symbols(): List<String> {
        return buildList {
            program.interfaceDefinitions.mapTo(this) { it.declaredIdentifier }
            program.functionDefinitions.mapTo(this) { it.declaredIdentifier }
        }
    }

    fun ast(): ProgramNode {
        validateSymbols()

        val interfaces = program.interfaceDefinitions
            .map { InterfaceDefinitionScope(this, it) }
            .map { it.ast() }

        val functions = program.functionDefinitions
            .map { FunctionDefinitionScope(this, it) }
            .map { it.ast() }

        return ProgramNode(interfaces, functions)
    }

}