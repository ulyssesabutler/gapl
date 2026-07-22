package com.uabutler.resolver.scope

import com.uabutler.ast.node.ProgramNode
import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.functions.FunctionDefinitionScope
import com.uabutler.resolver.scope.interfaces.InterfaceDefinitionScope
import com.uabutler.resolver.scope.util.declaredIdentifierToken

class ProgramScope(
    val program: CSTParser.ProgramContext,
    override val diagnostics: DiagnosticsCollector,
    override val definitions: DefinitionsCollector,
): Scope {

    override val parentScope: Scope? = null

    private val interfaces = program.interfaceDefinition().associateBy { it.declaredIdentifierToken.text!! }
    private val functions = program.functionDefinition().associateBy { it.declaredIdentifier!!.text!! }

    override fun resolveLocal(name: String): ResolvedSymbol? {
        interfaces[name]?.let { return ResolvedSymbol.Interface(it) }
        functions[name]?.let { return ResolvedSymbol.Function(it) }
        return null
    }

    override fun symbols(): List<DeclaredSymbol> {
        return interfaces.values.map { DeclaredSymbol(it.declaredIdentifierToken.text!!, it.declaredIdentifierToken) } +
            functions.values.map { DeclaredSymbol(it.declaredIdentifier!!.text!!, it.declaredIdentifier!!) }
    }

    fun ast(): ProgramNode {
        validateSymbols()

        val interfaceNodes = program.interfaceDefinition().map { InterfaceDefinitionScope(this, it).ast() }
        val functionNodes = program.functionDefinition().map { FunctionDefinitionScope(this, it).ast() }

        return ProgramNode(SourceSpan.of(program), interfaceNodes, functionNodes)
    }

}
