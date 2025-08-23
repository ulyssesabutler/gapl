package com.uabutler.resolver.scope.functions

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.expression.CSTCircuitExpression
import com.uabutler.cst.node.expression.CSTDeclaredCircuitNodeExpression
import com.uabutler.cst.node.functions.CSTFunctionDefinition
import com.uabutler.cst.node.functions.CSTNonConditionalCircuitStatement
import com.uabutler.cst.node.util.CSTInterfaceParameterDefinitionType
import com.uabutler.resolver.scope.ProgramScope
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.functions.circuits.CircuitStatementScope
import com.uabutler.resolver.scope.util.GenericParameterDefinitionScope

class FunctionDefinitionScope(
    override val parentScope: ProgramScope,
    val functionDefinition: CSTFunctionDefinition,
): Scope {

    companion object {
        fun declarationsFromCircuitExpression(expression: CSTCircuitExpression): List<CSTDeclaredCircuitNodeExpression> {
            return expression.connectedGroups.flatMap { it.groupedNodes }
                .filterIsInstance<CSTDeclaredCircuitNodeExpression>()
        }
    }

    private val parameters = functionDefinition.parameterDefinitions.associateBy { it.declaredIdentifier }
    // TODO: Conditional statements will need their own scope
    private val inputNodes = functionDefinition.inputs
    private val outputNodes = functionDefinition.outputs
    private val bodyNodes = functionDefinition.statements
        .filterIsInstance<CSTNonConditionalCircuitStatement>()
        .map { it.statement }
        .flatMap { it.connectedGroups }
        .flatMap { it.groupedNodes }
        .filterIsInstance<CSTDeclaredCircuitNodeExpression>()
    private val nodes = bodyNodes.associateBy { it.declaredIdentifier } + inputNodes.associateBy { it.declaredIdentifier } + outputNodes.associateBy { it.declaredIdentifier }

    val localSymbolTable = parameters + nodes

    override fun resolveLocal(name: String): CSTPersistent? {
        return localSymbolTable[name]
    }

    override fun resolveGlobal(name: String): CSTPersistent? {
        return resolveLocal(name) ?: parentScope.resolveGlobal(name)
    }

    override fun symbols(): List<String> {
        return buildList {
            functionDefinition.parameterDefinitions.mapTo(this) { it.declaredIdentifier }
            inputNodes.mapTo(this) { it.declaredIdentifier }
            outputNodes.mapTo(this) { it.declaredIdentifier }
            bodyNodes.mapTo(this) { it.declaredIdentifier }
        }
    }

    fun ast(): FunctionDefinitionNode {
        validateSymbols()

        val definitions = GenericParameterDefinitionScope(this, functionDefinition.parameterDefinitions).ast()

        return FunctionDefinitionNode(
            identifier = functionDefinition.declaredIdentifier.toIdentifier(),
            genericInterfaces = definitions.interfaceDefinitions,
            genericParameters = definitions.parameterDefinitions,
            inputFunctionIO = functionDefinition.inputs
                .map { FunctionIOScope(this, it).ast() },
            outputFunctionIO = functionDefinition.outputs
                .map { FunctionIOScope(this, it).ast() },
            statements = functionDefinition.statements
                .map { CircuitStatementScope(this, it).ast() },
        )
    }
}