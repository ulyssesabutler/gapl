package com.uabutler.module

import com.uabutler.ast.ProgramNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.references.IdentifierMap
import com.uabutler.references.ProgramScope

class ModuleBuilder(
    private val programAST: ProgramNode,
    private val identifierTable: IdentifierMap
) {

    private val programScope: ProgramScope = identifierTable.programScope
    private val concreteFunctions: Collection<FunctionDefinitionNode> = programAST.functions
        .filter { it.genericInterfaces.isEmpty() && it.genericParameters.isEmpty() }
    val concreteModules: Collection<Module> = concreteFunctions.map { Module(it) }

    companion object {
        fun fromAST(program: ProgramNode): ModuleBuilder {
            val identifiers = IdentifierMap.fromAST(program)
            return ModuleBuilder(
                programAST = program,
                identifierTable = identifiers,
            )
        }
    }


}