package com.uabutler.netlistir.builder

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.netlistir.builder.util.*
import com.uabutler.netlistir.netlist.*
import com.uabutler.netlistir.util.PredefinedFunction
import com.uabutler.util.AnonymousIdentifierGenerator

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
    ).also { netlistNodes[it.identifier] = it }.also { module.addInputNode(it) }

    private fun createOutputNode(
        identifier: String,
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    ) = OutputNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
    ).also { netlistNodes[it.identifier] = it }.also { module.addOutputNode(it) }

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
    ).also { netlistNodes[it.identifier] = it }.also { module.addBodyNode(it) }

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
    ).also { netlistNodes[it.identifier] = it }.also { module.addBodyNode(it) }

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
    ).also { netlistNodes[it.identifier] = it }.also { module.addBodyNode(it) }

    private fun buildInputWireVectorGroups(
        structure: InterfaceStructure,
        parent: Node,
    ) = InputWireVectorGroup(
        identifier = "only",
        parentNode = parent,
        structure = structure,
    )

    private fun buildOutputWireVectorGroups(
        structure: InterfaceStructure,
        parent: Node,
    ) = OutputWireVectorGroup(
        identifier = "only",
        parentNode = parent,
        structure = structure,
    )

    fun buildNodesIntoModule() {
        buildInputNodes()
        buildOutputNodes()
        buildBodyNodes()
    }

    private fun buildInputNodes() {
        inputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            createInputNode(
                identifier = astNode.identifier.value,
                outputWireVectorGroupsBuilder = { node ->
                    listOf(
                        buildOutputWireVectorGroups(
                            parent = node,
                            structure = interfaceStructure,
                        )
                    )
                }
            )
        }
    }

    private fun buildOutputNodes() {
        outputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            createOutputNode(
                identifier = astNode.identifier.value,
                inputWireVectorGroupsBuilder = { node ->
                    listOf(
                        buildInputWireVectorGroups(
                            parent = node,
                            structure = interfaceStructure,
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
        val inputs: List<WireVectorGroup.Projection<InputWireVector>>,
        val outputs: List<WireVectorGroup.Projection<OutputWireVector>>,
    ) {
        constructor(
            inputs: List<InputWireVectorGroup>,
            outputs: List<OutputWireVectorGroup>,
        ): this(inputs.map { it.projection() }, outputs.map { it.projection() })
    }

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
        previousOutputs: List<WireVectorGroup.Projection<OutputWireVector>>,
        currentInputs: List<WireVectorGroup.Projection<InputWireVector>>,
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
            module.connect(currentInput as InputWire, previousOutput as OutputWire)
        }
    }

    private fun processCircuitNodeExpressionNode(
        nodeExpression: CircuitNodeExpressionNode,
    ): IOGroups {
        return when (nodeExpression) {

            is DeclaredInterfaceCircuitExpressionNode -> {
                val structure = programContext.buildInterfaceWithContext(
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

            is DeclaredFunctionCircuitExpressionNode -> {
                val instantiationData = programContext.buildModuleInvocationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createNodeFromFunctionInvocation(nodeExpression.identifier.value, instantiationData)

                IOGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is DeclaredGenericFunctionCircuitExpressionNode -> {
                val parameterValue = parameterValuesContext[nodeExpression.functionIdentifier.value]!!

                val instantiationData = if (parameterValue is FunctionInstantiationParameterValue) {
                    parameterValue.value
                } else {
                    throw Exception("Expected module instantiation")
                }

                val node = createNodeFromFunctionInvocation(nodeExpression.identifier.value, instantiationData)

                IOGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is AnonymousFunctionCircuitExpressionNode -> {
                val instantiationData = programContext.buildModuleInvocationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createNodeFromFunctionInvocation(AnonymousIdentifierGenerator.genIdentifier(), instantiationData)

                IOGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is AnonymousGenericFunctionCircuitExpressionNode -> {
                val parameterValue = parameterValuesContext[nodeExpression.functionIdentifier.value]!!

                val instantiationData = if (parameterValue is FunctionInstantiationParameterValue) {
                    parameterValue.value
                } else {
                    throw Exception("Expected module instantiation")
                }

                val node = createNodeFromFunctionInvocation(AnonymousIdentifierGenerator.genIdentifier(), instantiationData)

                IOGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is ReferenceCircuitExpressionNode -> {
                val referencedNode = try {
                    netlistNodes[nodeExpression.identifier.value]!!
                } catch (_: NullPointerException) {
                    throw Exception("Unable to find node with identifier ${nodeExpression.identifier.value}")
                }

                val inputWireVectorGroup = referencedNode.inputWireVectorGroups.firstOrNull()
                val inputProjectionInformation = getProjectionValues(nodeExpression.singleAccesses)

                val outputWireVectorGroup = referencedNode.outputWireVectorGroups.firstOrNull()
                val outputProjection = getProjectionValues(nodeExpression.singleAccesses)

                IOGroups(
                    inputs = inputWireVectorGroup?.let { listOf(getInputWireVectorGroupProjection(it, inputProjectionInformation)) } ?: emptyList(),
                    outputs = outputWireVectorGroup?.let { listOf(getOutputWireVectorGroupProjection(it, outputProjection)) } ?: emptyList(),
                )
            }

            // TODO
            is ProtocolAccessorCircuitExpressionNode -> TODO()

            is RecordInterfaceConstructorExpressionNode -> TODO()

            is CircuitExpressionNodeCircuitExpression -> TODO()
        }
    }

    data class CurrentProjection(
        val identifier: List<String> = emptyList(),
        val indices: List<Int> = emptyList(),
        val range: IntRange? = null,
    )

    private fun getProjectionValues(
        accessNode: List<SingleAccessOperationNode>
    ) = accessNode.fold(CurrentProjection()) { current, accessNode ->
        // TODO: Validation
        when (accessNode) {
            is MemberAccessOperationNode -> {
                CurrentProjection(
                    identifier = current.identifier + accessNode.memberIdentifier.value,
                    indices = current.indices,
                )
            }
            is SingleArrayAccessOperationNode -> {
                val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                    staticExpression = accessNode.index,
                    context = parameterValuesContext,
                )

                CurrentProjection(
                    identifier = current.identifier,
                    indices = current.indices + index,
                )
            }
        }
    }

    private fun getInputWireVectorGroupProjection(
        wireVectorGroup: InputWireVectorGroup,
        projection: CurrentProjection,
    ): WireVectorGroup.Projection<InputWireVector> {
        val dimensions = InterfaceFlattener.fromInterfaceStructure(wireVectorGroup.gaplStructure).first().dimensions
        val indices = projection.indices

        return wireVectorGroup.projection(
            identifier = projection.identifier,
            range = getRange(dimensions, indices, projection.range)
        )
    }

    private fun getOutputWireVectorGroupProjection(
        wireVectorGroup: OutputWireVectorGroup,
        projection: CurrentProjection,
    ): WireVectorGroup.Projection<OutputWireVector> {
        val dimensions = InterfaceFlattener.fromInterfaceStructure(wireVectorGroup.gaplStructure).first().dimensions
        val indices = projection.indices

        return wireVectorGroup.projection(
            identifier = projection.identifier,
            range = getRange(dimensions, indices, projection.range)
        )
    }

    private fun getRange(
        dimensions: List<Int>,
        indices: List<Int>,
        finalRange: IntRange?,
    ): IntRange {
        val dimCount = dimensions.size
        val indexCount = indices.size

        require(indexCount < dimCount) { "Indices must specify fewer dimensions than the total." }

        // Compute stride for each dimension
        val strides = IntArray(dimCount) { 1 }
        for (i in dimCount - 2 downTo 0) {
            strides[i] = strides[i + 1] * dimensions[i + 1]
        }

        // Compute base offset from provided indices
        var offset = 0
        for (i in indices.indices) {
            offset += indices[i] * strides[i]
        }

        // Handle remaining dimensions
        return if (finalRange != null) {
            // We only slice into the next dimension after indices
            val nextDim = indexCount
            val elementSize = strides.getOrElse(nextDim + 1) { 1 }
            val start = offset + finalRange.first * strides[nextDim]
            val endInclusive = offset + finalRange.last * strides[nextDim] + elementSize - 1
            start..endInclusive
        } else {
            // We want the whole block starting at offset
            val totalSize = dimensions.drop(indexCount).reduce(Int::times)
            offset until offset + totalSize
        }
    }

    private fun createNodeFromFunctionInvocation(
        invocationName: String,
        invocation: Module.Invocation,
    ): BodyNode {
        // Step 1: Search predefined functions. If there is no predefined function with this name, search defined functions
        val matchingPredefinedFunction = PredefinedFunction.search(invocation)

        // Step 2: Create the node
        return if (matchingPredefinedFunction != null) {
            createPredefinedFunctionNode(
                identifier = invocationName,
                inputWireVectorGroupsBuilder = { node ->
                    matchingPredefinedFunction.inputs.map { it.toInputWireVectorGroup(node) }
                },
                outputWireVectorGroupsBuilder = { node ->
                    matchingPredefinedFunction.outputs.map { it.toOutputWireVectorGroup(node) }
                },
                predefinedFunction = matchingPredefinedFunction,
            )
        } else {
            // Find the matching defined function. This will also add it to the queue of modules to be built, if it hasn't been built yet.
            val instantiation = moduleInstantiationTracker.visitModule(invocation)

            createModuleInvocationNode(
                identifier = invocationName,
                inputWireVectorGroupsBuilder = { node ->
                    instantiation.input.map { input ->
                        InputWireVectorGroup(
                            identifier = input.name,
                            parentNode = node,
                            structure = input.interfaceStructure,
                        )
                    }
                },
                outputWireVectorGroupsBuilder = { node ->
                    instantiation.output.map { output ->
                        OutputWireVectorGroup(
                            identifier = output.name,
                            parentNode = node,
                            structure = output.interfaceStructure,
                        )
                    }
                },
                invocation = invocation,
            )
        }
    }

}