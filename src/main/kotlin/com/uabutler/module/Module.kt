package com.uabutler.module

import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.functions.circuits.*
import com.uabutler.util.AnonymousIdentifiers
import com.uabutler.util.StaticExpressionEvaluator

class Module(
    private val astNode: FunctionDefinitionNode,
) {

    val identifier = astNode.identifier.value

    override fun toString(): String {
        return "Module(" +
                "inputNodes=$inputNodes, " +
                "outputNodes=$outputNodes, " +
                "bodyNodes=$bodyNodes, " +
                "anonymousNodes=$anonymousNodes" +
                ")"
    }

    val inputNodes = astNode.inputFunctionIO.associate {
        it.identifier.value to ModuleNode.fromInterfaceExpressionNode(it.identifier.value, it.interfaceType, it)
    }
    val outputNodes = astNode.outputFunctionIO.associate {
        it.identifier.value to ModuleNode.fromInterfaceExpressionNode(it.identifier.value, it.interfaceType, it)
    }
    val bodyNodes = astNode.functionScope()!!.nodes.associate {
        it.identifier to ModuleNode.fromInterfaceExpressionNode(it.identifier, it.astNode.type, it.astNode)
    }
    val anonymousNodes: MutableMap<String, ModuleNode> = mutableMapOf()

    private fun getNodeByIdentifier(identifier: String): ModuleNode {
        return inputNodes[identifier] ?: outputNodes[identifier] ?: bodyNodes[identifier] ?: throw Exception("Unexpected identifier '$identifier'")
    }

    private val anonymousIdentifiers: AnonymousIdentifiers = AnonymousIdentifiers()

    init {
        getValidExpressions().forEach { expression ->
            processCircuitConnectionExpression(expression as CircuitConnectionExpressionNode)
        }
    }

    fun validateConnections(): Boolean {
        return outputNodes.values.all { validateConnectionsOfNode(it) }
    }

    private fun validateConnectionsOfNode(node: ModuleNode): Boolean {
        if (node in inputNodes.values) return true
        if (!node.hasInput()) return false
        return node.input.all { it.getInput().map { it.parentNode!! }.all { validateConnectionsOfNode(it) } }
    }

    private fun createAnonymousNode(
        nodeCreator: (identifier: String) -> ModuleNode,
    ): ModuleNode {
        val identifier = anonymousIdentifiers.generateIdentifier()
        anonymousNodes[identifier] = nodeCreator(identifier)
        return anonymousNodes[identifier]!!
    }

    private fun createAnonymousNode(
        input: List<ModuleNodeInterface>,
        output: List<ModuleNodeInterface>,
    ): ModuleNode {
        return createAnonymousNode { ModuleNode(input, output) }
    }

    /**
     * Filter out any conditional statements whose conditions are false
     */
    private fun getValidExpressions(statements: List<CircuitStatementNode>): List<CircuitExpressionNode> {
        if (statements.isEmpty()) return emptyList()

        val nonConditionalExpressions = statements
            .filterIsInstance<NonConditionalCircuitStatementNode>()
            .map { it.statement }

        // TODO: I'd have to think about how correct this is, since these are all technically in a different scope.
        val conditionalStatements = statements
            .filterIsInstance<ConditionalCircuitStatementNode>()
            .flatMap {
                // TODO: Support for non-concrete
                if (StaticExpressionEvaluator.evaluateConcrete(it.predicate) != 0) it.ifBody else it.elseBody
            }

        return nonConditionalExpressions + getValidExpressions(conditionalStatements)
    }

    private fun getValidExpressions(): List<CircuitExpressionNode> = getValidExpressions(astNode.statements)

    private fun processCircuitConnectionExpression(circuitConnectionExpression: CircuitConnectionExpressionNode) {
        circuitConnectionExpression.connectedExpression
            .map { processCircuitGroupExpression(it) }
            .zipWithNext()
            .forEach { (current, next) ->
                next.input.input.forEachIndexed { index, input ->
                    input.setInput(current.output.output[index])
                }
            }
    }

    data class CircuitNodeGroupInterface (
        val input: ModuleNode,
        val output: ModuleNode,
    )

    private fun processCircuitGroupExpression(circuitGroupExpressionNode: CircuitGroupExpressionNode): CircuitNodeGroupInterface {
        if (circuitGroupExpressionNode.expressions.size == 1) {
            val node = processCircuitNodeExpressionNode(circuitGroupExpressionNode.expressions.first())
            return CircuitNodeGroupInterface(
                input = node,
                output = node,
            )
        } else {
            val interiorNodes = circuitGroupExpressionNode.expressions.map { processCircuitNodeExpressionNode(it) }

            val exteriorInputInterfaces = interiorNodes
                .flatMap { it.input }
                .map {
                    val exteriorInputInterface = it.duplicate(identifier = it.identifier + "_exterior_front")
                    it.setInput(exteriorInputInterface)
                    exteriorInputInterface
                }

            val inputNode = createAnonymousNode(
                input = exteriorInputInterfaces.map { it.duplicate() }.onEach { it.identifier += "_input" },
                output = exteriorInputInterfaces.onEach { it.identifier += "_output" },
            )

            val exteriorOutputInterfaces = interiorNodes
                .flatMap { it.output }
                .map {
                    val exteriorOutputInterface = it.duplicate(identifier = it.identifier + "_exterior_back")
                    exteriorOutputInterface.setInput(it)
                    exteriorOutputInterface
                }

            val outputNode = createAnonymousNode(
                input = exteriorOutputInterfaces.onEach { it.identifier += "_input" },
                output = exteriorOutputInterfaces.map { it.duplicate() }.onEach { it.identifier += "_output" },
            )

            return CircuitNodeGroupInterface(
                input = inputNode,
                output = outputNode
            )
        }
    }

    private fun processCircuitNodeExpressionNode(circuitNodeExpressionNode: CircuitNodeExpressionNode): ModuleNode {
        when (circuitNodeExpressionNode) {
            is IdentifierCircuitExpressionNode -> {
                // TODO: We're assuming concrete for now, meaning this can only be a reference to a previous declaration
                return getNodeByIdentifier(circuitNodeExpressionNode.identifier.value)
            }
            is AnonymousNodeCircuitExpressionNode -> {
                return createAnonymousNode { ModuleNode.fromInterfaceExpressionNode(it, circuitNodeExpressionNode.type, circuitNodeExpressionNode) }
            }
            is DeclaredNodeCircuitExpressionNode -> {
                return getNodeByIdentifier(circuitNodeExpressionNode.identifier.value)
            }
            is ReferenceCircuitExpressionNode -> {
                // TODO: Access operators
                return getNodeByIdentifier(circuitNodeExpressionNode.identifier.value)
            }
            is RecordInterfaceConstructorExpressionNode -> TODO()
            is CircuitExpressionNodeCircuitExpression -> TODO()
        }
    }

}