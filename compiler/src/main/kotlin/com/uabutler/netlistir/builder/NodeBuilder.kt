package com.uabutler.netlistir.builder

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.netlistir.builder.util.*
import com.uabutler.netlistir.netlist.*
import com.uabutler.netlistir.util.PredefinedFunction

class NodeBuilder(
    val programContext: ProgramContext,
    val moduleInstantiationTracker: ModuleInstantiationTracker,
    val module: Module,
    val functionDefinitionAstNode: FunctionDefinitionNode,
    val interfaceValuesContext: Map<String, InterfaceStructure>,
    val parameterValuesContext: Map<String, ParameterValue<*>>,
) {
    val inputAstNodes = functionDefinitionAstNode.inputFunctionIO
    val outputAstNodes = functionDefinitionAstNode.outputFunctionIO
    val bodyAstNodes = functionDefinitionAstNode.statements

    val netlistNodes = mutableMapOf<String, Node>()

    private fun createInputNode(
        identifier: String,
        outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    ) = InputNode(
        identifier = identifier,
        parentModule = module,
        outputWireVectorGroupsBuilder = outputWireVectorGroupsBuilder,
    ).also { netlistNodes[it.identifier] = it }

    private fun createOutputNode(
        identifier: String,
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    ) = OutputNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
    ).also { netlistNodes[it.identifier] = it }

    private fun createModuleInvocationNode(
        identifier: String,
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
        outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
        invocation: Module.Invocation,
    ) = ModuleInvocationNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
        outputWireVectorGroupsBuilder = outputWireVectorGroupsBuilder,
        invocation = invocation,
    ).also { netlistNodes[it.identifier] = it }

    private fun createPredefinedFunctionNode(
        identifier: String,
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
        outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
        predefinedFunction: PredefinedFunction,
    ) = PredefinedFunctionNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
        outputWireVectorGroupsBuilder = outputWireVectorGroupsBuilder,
        predefinedFunction = predefinedFunction,
    ).also { netlistNodes[it.identifier] = it }

    private fun createPassThroughNode(
        identifier: String,
        // TODO: We need some way to represent the interface that will be the same on both sides
        //   For now, the user is just going to pinky promise they're the same
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
        outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    ) = PassThroughNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
        outputWireVectorGroupsBuilder = outputWireVectorGroupsBuilder,
    ).also { netlistNodes[it.identifier] = it }

    private fun buildInputWireVectorGroups(
        structure: List<FlatInterfaceWireVector>,
        parent: Node,
    ) = InputWireVectorGroup(
        identifier = "only",
        parentNode = parent,
        wireVectorsBuilder = { wireVectorGroup -> structure.map { buildInputWireVectors(it, wireVectorGroup) } },
    )

    private fun buildOutputWireVectorGroups(
        structure: List<FlatInterfaceWireVector>,
        parent: Node,
    ) = OutputWireVectorGroup(
        identifier = "only",
        parentNode = parent,
        wireVectorsBuilder = { wireVectorGroup -> structure.map { buildOutputWireVectors(it, wireVectorGroup) } },
    )

    private fun buildInputWireVectors(verilogWire: FlatInterfaceWireVector, parent: InputWireVectorGroup) = InputWireVector(
        identifier = verilogWire.name,
        parentGroup = parent,
        size = verilogWire.width,
    )

    private fun buildOutputWireVectors(verilogWire: FlatInterfaceWireVector, parent: OutputWireVectorGroup) = OutputWireVector(
        identifier = verilogWire.name,
        parentGroup = parent,
        size = verilogWire.width,
    )

    fun buildNodesIntoModule() {
        buildInputNodes()
        buildOutputNodes()
        buildBodyNodes()
    }

    private fun buildInputNodes() {
        inputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildFlatInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            createInputNode(
                identifier = astNode.identifier.value,
                outputWireVectorGroupsBuilder = { node ->
                    listOf(
                        buildOutputWireVectorGroups(
                            structure = interfaceStructure,
                            parent = node,
                        )
                    )
                }
            )
        }
    }

    private fun buildOutputNodes() {
        outputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildFlatInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            createOutputNode(
                identifier = astNode.identifier.value,
                inputWireVectorGroupsBuilder = { node ->
                    listOf(
                        buildInputWireVectorGroups(
                            structure = interfaceStructure,
                            parent = node,
                        )
                    )
                }
            )
        }
    }

    private fun buildBodyNodes() {
        // Step 1: Let's simplify this a bit by evaluating all the conditionals
        val circuitExpressions = bodyAstNodes.flatMap { getCircuitExpressionsFromCircuitStatement(it) }

        // Step 2: Process the expressions to create nodes and connect them
        circuitExpressions
            .map { it as CircuitConnectionExpressionNode } // TODO: This is currently the only supported type
            .map { processCircuitConnectionExpressionNode(it) }
    }

    private fun getCircuitExpressionsFromCircuitStatement(
        circuitStatementNode: CircuitStatementNode,
    ): List<CircuitExpressionNode> {
        return when (circuitStatementNode) {
            is NonConditionalCircuitStatementNode -> listOf(circuitStatementNode.statement)
            is ConditionalCircuitStatementNode -> {
                val predicateValue = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                    staticExpression = circuitStatementNode.predicate,
                    context = parameterValuesContext,
                )

                if (predicateValue != 0) {
                    circuitStatementNode.ifBody.flatMap { getCircuitExpressionsFromCircuitStatement(it) }
                } else {
                    circuitStatementNode.elseBody.flatMap { getCircuitExpressionsFromCircuitStatement(it) }
                }
            }
        }
    }

    data class IOGroups(
        val inputs: List<InputWireVectorGroup>,
        val outputs: List<OutputWireVectorGroup>,
    )

    /* Each circuit connection expression will be a list of circuit groups separated by the connection operator.
     * For example, we might have groups "a,b,c", "d,e", and "f". Then, a connection expression "a,b,c => d,e => f"
     *
     */
    private fun processCircuitConnectionExpressionNode(
        connectionExpression: CircuitConnectionExpressionNode,
    ) {
        connectionExpression.connectedExpression
            .map { processCircuitGroupExpressionNode(it) }
            .zipWithNext()
            .map { (previous, current) -> previous.outputs to current.inputs }
            .forEach { (previousOutputs, currentInputs) ->
                createOutputWireVectorGroupConnections(
                    previousOutputs = previousOutputs,
                    currentInputs = currentInputs,
                )
            }
    }

    private fun processCircuitGroupExpressionNode(
        groupExpression: CircuitGroupExpressionNode,
    ): IOGroups {
        val ioGroups = groupExpression.expressions
            .map { processCircuitNodeExpressionNode(it) }
            .flatMap { it.inputs.zip(it.outputs) }

        return IOGroups(
            inputs = ioGroups.map { it.first },
            outputs = ioGroups.map { it.second },
        )
    }

    private fun createOutputWireVectorGroupConnections(
        previousOutputs: List<OutputWireVectorGroup>,
        currentInputs: List<InputWireVectorGroup>,
    ) {
        // TODO: This is a very lazy, overly simplistic validation.
        //   There are definition interface structures that will match here that shouldn't actually match.
        //   For example, wire[5][10] and wire[50].

        if (previousOutputs.size != currentInputs.size) throw Exception("Mismatched")

        val wirePairs = previousOutputs.zip(currentInputs).flatMap { (previous, current) ->
            if (previous.wireVectors.size != current.wireVectors.size) throw Exception("Mismatched")
            previous.wireVectors.zip(current.wireVectors)
        }.flatMap { (previous, current) ->
            if (previous.wires.size != current.wires.size) throw Exception("Mismatched")
            previous.wires.zip(current.wires)
        }

        wirePairs.forEach { (previousOutput, currentInput) ->
            module.connect(currentInput, previousOutput)
        }
    }

    private fun processCircuitNodeExpressionNode(
        nodeExpression: CircuitNodeExpressionNode,
    ): IOGroups {
        return when (nodeExpression) {
            is DeclaredInterfaceCircuitExpressionNode -> {
                val structure = programContext.buildFlatInterfaceWithContext(
                    node = nodeExpression.type,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createPassThroughNode(
                    identifier = nodeExpression.identifier.value,
                    inputWireVectorGroupsBuilder = { node ->
                        listOf(
                            buildInputWireVectorGroups(
                                structure = structure,
                                parent = node,
                            )
                        )
                    },
                    outputWireVectorGroupsBuilder = { node ->
                        listOf(
                            buildOutputWireVectorGroups(
                                structure = structure,
                                parent = node,
                            )
                        )
                    },
                )

                IOGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is DeclaredFunctionCircuitExpressionNode -> TODO()
            is DeclaredGenericFunctionCircuitExpressionNode -> TODO()
            is AnonymousFunctionCircuitExpressionNode -> TODO()
            is AnonymousGenericFunctionCircuitExpressionNode -> TODO()
            is ReferenceCircuitExpressionNode -> TODO()

            is ProtocolAccessorCircuitExpressionNode -> TODO()

            is RecordInterfaceConstructorExpressionNode -> TODO()

            is CircuitExpressionNodeCircuitExpression -> TODO()
        }
    }


}