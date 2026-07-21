package com.uabutler.resolver

import com.uabutler.ast.node.ProgramNode
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.ProgramScope

object Resolver {

    data class AnalysisResult(val ast: ProgramNode, val diagnostics: List<Diagnostic>)

    fun analyze(program: CSTParser.ProgramContext): AnalysisResult {
        val diagnostics = DiagnosticsCollector()
        val ast = ProgramScope(program, diagnostics).ast()

        return AnalysisResult(ast, diagnostics.diagnostics())
    }

}
