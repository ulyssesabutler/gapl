package com.uabutler.resolver

import com.uabutler.ast.node.ProgramNode
import com.uabutler.cst.node.CSTProgram
import com.uabutler.resolver.scope.ProgramScope

object Resolver {

    fun cstToAst(program: CSTProgram): ProgramNode {
        return ProgramScope(program).ast()
    }

}