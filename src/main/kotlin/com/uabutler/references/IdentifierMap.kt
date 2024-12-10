package com.uabutler.references

import com.uabutler.ast.ProgramNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.functions.circuits.*
import com.uabutler.ast.interfaces.RecordInterfaceDefinitionNode

class IdentifierMap private constructor(
    val programScope: ProgramScope
) {

    companion object {
        fun fromAST(root: ProgramNode) = IdentifierMap(programScopeFromNode(root))

        private fun programScopeFromNode(node: ProgramNode): ProgramScope {
            // Create scope
            val programScope = ProgramScope(
                interfaceDeclarations = node.interfaces.map { InterfaceDeclaration(it.identifier.value, it) },
                functionDeclarations = node.functions.map { FunctionDeclaration(it.identifier.value, it) },
            )

            // Create and add children scopes
            node.interfaces
                .filterIsInstance<RecordInterfaceDefinitionNode>()
                .map { interfaceScopeFromNode(it, programScope) }
                .onEach { programScope.addInterfaceScope(it) }

            node.functions
                .map { functionScopeFromNode(it, programScope) }
                .onEach { programScope.addFunctionScope(it) }

            return programScope
        }

        private fun interfaceScopeFromNode(node: RecordInterfaceDefinitionNode, program: ProgramScope): InterfaceScope {
            // Create and return scope
            return InterfaceScope(
                genericInterfaces = node.genericInterfaces.map { GenericInterfaceDeclaration(it.identifier.value, it) },
                genericParameters = node.genericParameters.map { GenericParameterDeclaration(it.identifier.value, it) },
                ports = node.ports.map { PortDeclaration(it.identifier.value, it) },
                program = program,
            )
        }

        private fun declarationsFromCircuitExpression(circuitExpression: CircuitExpressionNode): List<NodeDeclaration> {
            if (circuitExpression !is CircuitConnectionExpressionNode) return emptyList()

            return circuitExpression.connectedExpression
                .flatMap { it.expressions }
                .flatMap {
                    when (it) {
                        is DeclaredNodeCircuitExpressionNode -> listOf(NodeDeclaration(it.identifier.value, it))
                        is CircuitExpressionNodeCircuitExpression -> declarationsFromCircuitExpression(it.expression)
                        else -> emptyList()
                    }
                }
        }

        private fun declarationsFromCircuitStatements(circuitStatements: Collection<CircuitStatementNode>): Collection<NodeDeclaration> {
            return circuitStatements
                .filterIsInstance<NonConditionalCircuitStatementNode>()
                .flatMap { declarationsFromCircuitExpression(it.statement) }
        }

        data class ConnectionScopeSubScopes(
            val interfaceConstructors: List<InterfaceConstructorScope>,
            val ifBodies: List<IfBodyScope>,
        )

        private fun scopesFromCircuitExpression(circuitExpression: CircuitExpressionNode, parent: Scope): ConnectionScopeSubScopes{
            if (circuitExpression !is CircuitConnectionExpressionNode) return ConnectionScopeSubScopes(emptyList(), emptyList())

            val interfaceConstructors = circuitExpression.connectedExpression
                .flatMap { it.expressions }
                .flatMap {
                    when (it) {
                        is RecordInterfaceConstructorExpressionNode -> listOf(interfaceConstructorScopeFromNode(it, parent))
                        is CircuitExpressionNodeCircuitExpression -> scopesFromCircuitExpression(it.expression, parent).interfaceConstructors
                        else -> emptyList()
                    }
                }

            return ConnectionScopeSubScopes(interfaceConstructors, emptyList())
        }

        private fun scopesFromCircuitStatements(circuitStatements: Collection<CircuitStatementNode>, parent: Scope): ConnectionScopeSubScopes {
            val circuitExpressionScopes = circuitStatements
                .filterIsInstance<NonConditionalCircuitStatementNode>()
                .map { it.statement }
                .map { scopesFromCircuitExpression(it, parent) }

            val conditionalScopes = circuitStatements
                .filterIsInstance<ConditionalCircuitStatementNode>()
                .flatMap { ifBodyScopesFromNode(it, parent) }

            val interfaceConstructors = circuitExpressionScopes.flatMap { it.interfaceConstructors }
            val ifBodies = circuitExpressionScopes.flatMap { it.ifBodies } + conditionalScopes

            return ConnectionScopeSubScopes(interfaceConstructors, ifBodies)
        }

        private fun interfaceConstructorScopeFromNode(node: RecordInterfaceConstructorExpressionNode, scope: Scope): InterfaceConstructorScope {
            val interfaceConstructorScope = InterfaceConstructorScope(
                nodes = declarationsFromCircuitStatements(node.statements),
                parent = scope,
            )

            val children = scopesFromCircuitStatements(node.statements, interfaceConstructorScope)

            children.interfaceConstructors.forEach { interfaceConstructorScope.addInterfaceConstructor(it) }
            children.ifBodies.forEach { interfaceConstructorScope.addIfBody(it) }

            return interfaceConstructorScope
        }

        private fun ifBodyScopesFromNode(node: ConditionalCircuitStatementNode, parent: Scope): Collection<IfBodyScope> {
            val ifBodyScope = IfBodyScope(
                nodes = declarationsFromCircuitStatements(node.ifBody),
                parent = parent,
            )

            val elseBodyScope = IfBodyScope(
                nodes = declarationsFromCircuitStatements(node.elseBody),
                parent = parent,
            )

            val childrenFromIf = scopesFromCircuitStatements(node.ifBody, ifBodyScope)
            childrenFromIf.interfaceConstructors.forEach { ifBodyScope.addInterfaceConstructor(it) }
            childrenFromIf.ifBodies.forEach { ifBodyScope.addIfBody(it) }

            val childrenFromElse = scopesFromCircuitStatements(node.elseBody, elseBodyScope)
            childrenFromElse.interfaceConstructors.forEach { ifBodyScope.addInterfaceConstructor(it) }
            childrenFromElse.ifBodies.forEach { ifBodyScope.addIfBody(it) }

            return listOf(ifBodyScope, elseBodyScope)
        }

        private fun functionScopeFromNode(node: FunctionDefinitionNode, program: ProgramScope): FunctionScope {
            val functionScope = FunctionScope(
                genericInterfaces = node.genericInterfaces.map { GenericInterfaceDeclaration(it.identifier.value, it) },
                genericParameters = node.genericParameters.map { GenericParameterDeclaration(it.identifier.value, it) },
                inputs = node.inputFunctionIO.map { FunctionIODeclaration(it.identifier.value, it) },
                outputs = node.outputFunctionIO.map { FunctionIODeclaration(it.identifier.value, it) },
                nodes = declarationsFromCircuitStatements(node.statements),
                program = program,
            )

            val children = scopesFromCircuitStatements(node.statements, functionScope)

            children.interfaceConstructors.forEach { functionScope.addInterfaceConstructor(it) }
            children.ifBodies.forEach { functionScope.addIfBody(it) }

            return functionScope
        }
    }

}