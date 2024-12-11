package com.uabutler.module

import com.uabutler.ast.ProgramNode
import com.uabutler.ast.functions.FunctionDefinitionNode

object ModuleBuilder {
    fun astConcreteFunctions(program: ProgramNode): Collection<FunctionDefinitionNode> {
        return program.functions
            .filter { it.genericInterfaces.isEmpty() && it.genericParameters.isEmpty() }
    }
}