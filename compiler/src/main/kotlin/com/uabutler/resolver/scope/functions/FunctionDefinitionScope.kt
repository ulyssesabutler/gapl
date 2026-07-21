package com.uabutler.resolver.scope.functions

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.DeclaredSymbol
import com.uabutler.resolver.scope.ProgramScope
import com.uabutler.resolver.scope.ResolvedSymbol
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.functions.circuits.CircuitStatementScope
import com.uabutler.resolver.scope.util.GenericParameterDefinitionScope
import com.uabutler.resolver.scope.util.toIdentifierNode

class FunctionDefinitionScope(
    override val parentScope: ProgramScope,
    val functionDefinition: CSTParser.FunctionDefinitionContext,
): Scope {

    companion object {
        fun declarationsFromCircuitExpression(expression: CSTParser.CircuitExpressionContext): List<CSTParser.DeclaredCircuitExpressionContext> {
            return expression.circuitGroupExpression()
                .flatMap { it.circuitNodeExpression() }
                .filterIsInstance<CSTParser.DeclaredCircuitExpressionContext>()
        }
    }

    private val parameterDefinitions = functionDefinition.parameterDefinitionList()?.parameterDefinition() ?: emptyList()
    private val parameters = parameterDefinitions.associateBy { it.declaredIdenfier!!.text!! }

    private val inputNodes = functionDefinition.input?.functionIO() ?: emptyList()
    private val outputNodes = functionDefinition.output?.functionIO() ?: emptyList()

    private val bodyNodes = functionDefinition.circuitStatement()
        .filterIsInstance<CSTParser.NonConditionalCircuitStatementContext>()
        .map { it.circuitExpression()!! }
        .flatMap { declarationsFromCircuitExpression(it) }

    private val nodesByIdentifier = bodyNodes.associateBy { it.declaredIdentifier!!.text!! } +
        inputNodes.associateBy { it.declaredIdentifier!!.text!! } +
        outputNodes.associateBy { it.declaredIdentifier!!.text!! }

    override fun resolveLocal(name: String): ResolvedSymbol? {
        parameters[name]?.let { return ResolvedSymbol.Parameter(it) }

        return when (val node = nodesByIdentifier[name]) {
            is CSTParser.DeclaredCircuitExpressionContext -> ResolvedSymbol.CircuitNode(node)
            is CSTParser.FunctionIOContext -> ResolvedSymbol.FunctionIO(node)
            else -> null
        }
    }

    override fun symbols(): List<DeclaredSymbol> {
        return buildList {
            parameterDefinitions.mapTo(this) { DeclaredSymbol(it.declaredIdenfier!!.text!!, it.declaredIdenfier!!) }
            inputNodes.mapTo(this) { DeclaredSymbol(it.declaredIdentifier!!.text!!, it.declaredIdentifier!!) }
            outputNodes.mapTo(this) { DeclaredSymbol(it.declaredIdentifier!!.text!!, it.declaredIdentifier!!) }
            bodyNodes.mapTo(this) { DeclaredSymbol(it.declaredIdentifier!!.text!!, it.declaredIdentifier!!) }
        }
    }

    fun ast(): FunctionDefinitionNode {
        validateSymbols()

        val span = SourceSpan.of(functionDefinition)
        val definitions = GenericParameterDefinitionScope(this, parameterDefinitions).ast()

        return FunctionDefinitionNode(
            span = span,
            identifier = functionDefinition.declaredIdentifier!!.toIdentifierNode(),
            genericInterfaces = definitions.interfaceDefinitions,
            genericParameters = definitions.parameterDefinitions,
            inputFunctionIO = inputNodes.map { FunctionIOScope(this, it).ast() },
            outputFunctionIO = outputNodes.map { FunctionIOScope(this, it).ast() },
            statements = functionDefinition.circuitStatement().map { CircuitStatementScope(this, it).ast() },
        )
    }
}
