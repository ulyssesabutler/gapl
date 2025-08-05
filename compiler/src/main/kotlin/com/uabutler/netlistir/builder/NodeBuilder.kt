package com.uabutler.netlistir.builder

import com.uabutler.ast.node.InOutTransformerModeNode
import com.uabutler.ast.node.InTransformerModeNode
import com.uabutler.ast.node.OutTransformerModeNode
import com.uabutler.ast.node.TransformerModeNode
import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.functions.FunctionExpressionInstantiationNode
import com.uabutler.ast.node.functions.FunctionExpressionNode
import com.uabutler.ast.node.functions.FunctionExpressionReferenceNode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.ast.node.interfaces.InterfaceExpressionNode
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
    ).also { netlistNodes[it.name()] = it }.also { module.addInputNode(it) }

    private fun createOutputNode(
        identifier: String,
        inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    ) = OutputNode(
        identifier = identifier,
        parentModule = module,
        inputWireVectorGroupsBuilder = inputWireVectorGroupsBuilder,
    ).also { netlistNodes[it.name()] = it }.also { module.addOutputNode(it) }

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
    ).also { netlistNodes[it.name()] = it }.also { module.addBodyNode(it) }

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
    ).also { netlistNodes[it.name()] = it }.also { module.addBodyNode(it) }

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
    ).also { netlistNodes[it.name()] = it }.also { module.addBodyNode(it) }

    private fun createTransformerNode(
        identifier: String,
        // TODO: We need some way to represent the interface that will be the same on both sides
        //   For now, the user is just going to pinky promise they're the same
        externalIngressWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
        internalIngressWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
        externalEgressWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
        internalEgressWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    ) = TransformerNode(
        identifier = identifier,
        parentModule = module,
        externalIngressWireVectorGroupsBuilder = externalIngressWireVectorGroupsBuilder,
        internalIngressWireVectorGroupsBuilder = internalIngressWireVectorGroupsBuilder,
        externalEgressWireVectorGroupsBuilder = externalEgressWireVectorGroupsBuilder,
        internalEgressWireVectorGroupsBuilder = internalEgressWireVectorGroupsBuilder,
    ).also { netlistNodes[it.name()] = it }.also { module.addBodyNode(it) }

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
        circuitExpressions.forEach { processCircuitExpressionNode(it) }
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
        companion object {
            fun fromWireVectorGroups(
                inputs: List<InputWireVectorGroup>,
                outputs: List<OutputWireVectorGroup>,
            ) = IOGroups(
                inputs = inputs.map { it.projection() },
                outputs = outputs.map { it.projection() },
            )
        }
    }

    private fun processCircuitExpressionNode(
        expression: CircuitExpressionNode,
    ): IOGroups {
        return when (expression) {
            is CircuitConnectionExpressionNode -> processCircuitConnectionExpressionNode(expression)
        }
    }

    private fun processCircuitConnectionExpressionNode(
        expression: CircuitConnectionExpressionNode,
    ): IOGroups {
        val connectedComponents = expression.connectedExpression
            .map { processCircuitGroupExpressionNode(it) }

        connectedComponents
            .zipWithNext()
            .map { (previous, current) -> previous.outputs to current.inputs }
            .forEach { (previousOutputs, currentInputs) ->
                createOutputWireVectorGroupConnections(
                    previousOutputs = previousOutputs,
                    currentInputs = currentInputs,
                )
            }

        return IOGroups(
            inputs = connectedComponents.first().inputs,
            outputs = connectedComponents.last().outputs,
        )
    }

    private fun processCircuitGroupExpressionNode(
        groupExpression: CircuitGroupExpressionNode,
    ): IOGroups {
        val ioGroups = groupExpression.expressions
            .map { processCircuitNodeExpressionNode(it) }

        return IOGroups(
            inputs = ioGroups.flatMap { it.inputs },
            outputs = ioGroups.flatMap { it.outputs },
        )
    }

    private fun createOutputWireVectorGroupConnections(
        previousOutputs: List<WireVectorGroup.Projection<OutputWireVector>>,
        currentInputs: List<WireVectorGroup.Projection<InputWireVector>>,
    ) {
        // TODO: This is a very lazy, overly simplistic validation.
        //   There are definition interface structures that will match here that shouldn't actually match.
        //   For example, wire[5][10] and wire[50].

        fun checkForMismatch(s1: Int, s2: Int) {
            if (s1 != s2) {
                // TODO: Also, this error handling is shit. Ideally, we would show the user which connection is mismatched.
                val gaplModuleName = currentInputs.first().sourceGroup.parentNode.parentModule.invocation.gaplFunctionName
                val previousOutputNodeName = previousOutputs.first().sourceGroup.parentNode.name()
                val currentInputNodeName = currentInputs.first().sourceGroup.parentNode.name()

                throw Exception("Mismatch in $gaplModuleName of $previousOutputNodeName ($s1) to $currentInputNodeName ($s2)")
            }
        }

        checkForMismatch(previousOutputs.size, currentInputs.size)

        val wirePairs = previousOutputs.zip(currentInputs).flatMap { (previous, current) ->
            checkForMismatch(previous.wireVectors.size, current.wireVectors.size)
            previous.wireVectors.zip(current.wireVectors)
        }.flatMap { (previous, current) ->
            checkForMismatch(previous.wires.size, current.wires.size)
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

            is CircuitNodeCreationExpressionNode -> {
                val nodeIdentifier = nodeExpression.identifier?.value ?: AnonymousIdentifierGenerator.genIdentifier()

                val node = createNodeFromNodeInteriorWithIdentifier(
                    nodeIdentifier = nodeIdentifier,
                    interior = nodeExpression.interior
                )

                IOGroups(
                    inputs = node.inputWireVectorGroups.map { it.projection() },
                    outputs = node.outputWireVectorGroups.map { it.projection() },
                )
            }

            is CircuitNodeLiteralExpressionNode -> {
                // TODO: This requires some kind of type inference, since we need to know the width of the literal
                TODO()
            }

            is CircuitNodeReferenceExpressionNode -> {
                val referencedNode = try {
                    netlistNodes[nodeExpression.identifier.value]!!
                } catch (_: NullPointerException) {
                    throw Exception("Unable to find node with identifier ${nodeExpression.identifier.value}")
                }

                val projectionInformation = getProjectionValues(nodeExpression.singleAccesses, nodeExpression.multipleAccess)
                val inputWireVectorGroup = referencedNode.inputWireVectorGroups.firstOrNull()
                val outputWireVectorGroup = referencedNode.outputWireVectorGroups.firstOrNull()

                IOGroups(
                    inputs = inputWireVectorGroup?.let { listOf(getInputWireVectorGroupProjection(it, projectionInformation)) } ?: emptyList(),
                    outputs = outputWireVectorGroup?.let { listOf(getOutputWireVectorGroupProjection(it, projectionInformation)) } ?: emptyList(),
                )
            }

        }
    }

    private fun createNodeFromNodeInteriorWithIdentifier(
        nodeIdentifier: String,
        interior: CircuitNodeInteriorNode,
    ): BodyNode {
        return when (interior) {
            is CircuitNodeFunctionInteriorNode -> {
                createNodeFromFunctionExpression(
                    nodeIdentifier = nodeIdentifier,
                    nodeExpression = interior.function,
                )
            }
            is CircuitNodeInterfaceInteriorNode -> {
                createNodeFromInterfaceExpression(
                    nodeIdentifier = nodeIdentifier,
                    nodeExpression = interior.interfaceExpression,
                )
            }
            is CircuitNodeInterfaceTransformerInteriorNode -> {
                createNodeFromTransformer(
                    nodeIdentifier = nodeIdentifier,
                    transformerModeNode = interior.mode,
                    interfaceExpression = interior.interfaceExpression,
                    circuitNodeInterfaceTransformerNode = interior.interfaceTransformer,
                )
            }
        }
    }

    data class CurrentProjection(
        val identifier: List<String> = emptyList(),
        val indices: List<Int> = emptyList(),
        val range: IntRange? = null,
    )

    private fun getProjectionValues(
        singleAccessNodes: List<SingleAccessOperationNode>,
        multipleAccessNode: MultipleAccessOperationNode?,
    ): CurrentProjection {
        val initialProjection = if (multipleAccessNode != null) {
            val start = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                staticExpression = multipleAccessNode.startIndex,
                context = parameterValuesContext,
            )
            val end = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                staticExpression = multipleAccessNode.endIndex,
                context = parameterValuesContext,
            )

            CurrentProjection(range = start..end)
        } else {
            CurrentProjection()
        }

        return singleAccessNodes.fold(initialProjection) { current, accessNode ->
            when (accessNode) {
                is MemberAccessOperationNode -> {
                    CurrentProjection(
                        identifier = current.identifier + accessNode.memberIdentifier.value,
                        indices = current.indices,
                        range = current.range,
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
                        range = current.range,
                    )
                }
            }
        }
    }

    private fun getInputWireVectorGroupProjection(
        wireVectorGroup: InputWireVectorGroup,
        projection: CurrentProjection,
    ): WireVectorGroup.Projection<InputWireVector> {
        return wireVectorGroup.projection(
            members = projection.identifier,
            indices = projection.indices,
            range = projection.range
        )
    }

    private fun getOutputWireVectorGroupProjection(
        wireVectorGroup: OutputWireVectorGroup,
        projection: CurrentProjection,
    ): WireVectorGroup.Projection<OutputWireVector> {
        return wireVectorGroup.projection(
            members = projection.identifier,
            indices = projection.indices,
            range = projection.range
        )
    }

    private fun createNodeFromTransformer(
        nodeIdentifier: String,
        transformerModeNode: TransformerModeNode,
        interfaceExpression: InterfaceExpressionNode,
        circuitNodeInterfaceTransformerNode: CircuitNodeInterfaceTransformerNode,
    ): TransformerNode {
        val structure = programContext.buildInterfaceWithContext(
            node = interfaceExpression,
            interfaceValuesContext = interfaceValuesContext,
            parameterValuesContext = parameterValuesContext,
        )

        return when (circuitNodeInterfaceTransformerNode) {
            is CircuitNodeInterfaceListTransformerNode -> {
                if (structure !is VectorInterfaceStructure) throw Exception("Expected list interface structure for list transformer")
                createNodeFromListTransformer(
                    nodeIdentifier = nodeIdentifier,
                    transformerModeNode = transformerModeNode,
                    interfaceStructure = structure,
                    circuitNodeInterfaceTransformerNode = circuitNodeInterfaceTransformerNode,
                )
            }
            is CircuitNodeInterfaceRecordTransformerNode -> {
                if (structure !is RecordInterfaceStructure) throw Exception("Expected record interface structure for record transformer")
                createNodeFromRecordTransformer(
                    nodeIdentifier = nodeIdentifier,
                    transformerModeNode = transformerModeNode,
                    interfaceStructure = structure,
                    circuitNodeInterfaceTransformerNode = circuitNodeInterfaceTransformerNode,
                )
            }
        }
    }

    private fun createNodeFromRecordTransformer(
        nodeIdentifier: String,
        transformerModeNode: TransformerModeNode,
        interfaceStructure: RecordInterfaceStructure,
        circuitNodeInterfaceTransformerNode: CircuitNodeInterfaceRecordTransformerNode,
    ): TransformerNode {
        return createNodesFromTransformerExpressions(
            nodeIdentifier = nodeIdentifier,
            transformerModeNode = transformerModeNode,
            interfaceStructure = interfaceStructure,
            subItemsToTransform = interfaceStructure.ports.map { it.key to it.value },
            subItemsTransformers = circuitNodeInterfaceTransformerNode.expressions.map { it.port.value to it.expression }
        )
    }

    private fun createNodeFromListTransformer(
        nodeIdentifier: String,
        transformerModeNode: TransformerModeNode,
        interfaceStructure: VectorInterfaceStructure,
        circuitNodeInterfaceTransformerNode: CircuitNodeInterfaceListTransformerNode,
    ): TransformerNode {
        return createNodesFromTransformerExpressions(
            nodeIdentifier = nodeIdentifier,
            transformerModeNode = transformerModeNode,
            interfaceStructure = interfaceStructure,
            subItemsToTransform = List(interfaceStructure.size) { index -> index.toString() to interfaceStructure.vectoredInterface },
            subItemsTransformers = circuitNodeInterfaceTransformerNode.expressions.map {
                val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                    staticExpression = it.index,
                    context = parameterValuesContext,
                ).toString()

                index to it.expression
            }
        )
    }

    private fun createNodesFromTransformerExpressions(
        nodeIdentifier: String,
        transformerModeNode: TransformerModeNode,
        interfaceStructure: InterfaceStructure,
        subItemsToTransform: List<Pair<String, InterfaceStructure>>,
        subItemsTransformers: List<Pair<String, CircuitExpressionNode>>,
    ): TransformerNode {
        // Step 1: Validation
        val providedTransformers = subItemsTransformers.map { it.first }

        val duplicate = providedTransformers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
        if (duplicate != null) throw Exception("Duplicate transformation rule: $duplicate")

        val subItemsToTransformSet = subItemsToTransform.map { it.first }.toSet()
        val nonExistent = providedTransformers.firstOrNull { it !in subItemsToTransformSet }
        if (nonExistent != null) throw Exception("Nonexistent transformation rule: $nonExistent")

        // Step 2: Create the transformation nodes
        val transformers = subItemsTransformers.associate { it.first to it.second }
        val transformerIOGroups = subItemsToTransform.map { (subItemToTransform, interfaceStructure) ->
            val transformer = transformers[subItemToTransform]

            if (transformer == null) {
                val node = createNodeFromInterfaceStructure(
                    nodeIdentifier = subItemToTransform,
                    structure = interfaceStructure,
                )

                IOGroups.fromWireVectorGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            } else {
                processCircuitExpressionNode(transformer)
            }
        }

        val interiorIOGroup = IOGroups(
            inputs = transformerIOGroups.flatMap { it.inputs },
            outputs = transformerIOGroups.flatMap { it.outputs },
        )

        // Step 3: Create the transformer node
        val externalIngressBuilder = { node: Node -> listOf(buildInputWireVectorGroups(interfaceStructure, node)) }
        val internalIngressBuilder = { node: Node -> listOf(buildOutputWireVectorGroups(interfaceStructure, node)) }

        val externalEgressBuilder = { node: Node -> listOf(buildOutputWireVectorGroups(interfaceStructure, node)) }
        val internalEgressBuilder = { node: Node -> listOf(buildInputWireVectorGroups(interfaceStructure, node)) }

        val node = createTransformerNode(
            identifier = nodeIdentifier,
            externalIngressWireVectorGroupsBuilder = externalIngressBuilder,
            internalIngressWireVectorGroupsBuilder = internalIngressBuilder,
            externalEgressWireVectorGroupsBuilder = externalEgressBuilder,
            internalEgressWireVectorGroupsBuilder = internalEgressBuilder,
        )

        // Step 4: Connect the transformer node
        val hasIngress = transformerModeNode is InTransformerModeNode || transformerModeNode is InOutTransformerModeNode
        val hasEgress = transformerModeNode is OutTransformerModeNode || transformerModeNode is InOutTransformerModeNode

        if (hasIngress) {
            createOutputWireVectorGroupConnections(
                previousOutputs = node.internalIngressWireVectorGroups.map { it.projection() },
                currentInputs = interiorIOGroup.inputs,
            )
        }

        if (hasEgress) {
            createOutputWireVectorGroupConnections(
                previousOutputs = interiorIOGroup.outputs,
                currentInputs = node.internalEgressWireVectorGroups.map { it.projection() },
            )
        }

        return node
    }

    private fun createNodeFromInterfaceExpression(
        nodeIdentifier: String,
        nodeExpression: InterfaceExpressionNode,
    ): PassThroughNode {
        val structure = programContext.buildInterfaceWithContext(
            node = nodeExpression,
            interfaceValuesContext = interfaceValuesContext,
            parameterValuesContext = parameterValuesContext,
        )

        return createNodeFromInterfaceStructure(
            nodeIdentifier = nodeIdentifier,
            structure = structure,
        )
    }

    fun createNodeFromInterfaceStructure(
        nodeIdentifier: String,
        structure: InterfaceStructure,
    ): PassThroughNode {
        return createPassThroughNode(
            identifier = nodeIdentifier,
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
    }

    private fun createNodeFromFunctionExpression(
        nodeIdentifier: String,
        nodeExpression: FunctionExpressionNode,
    ): BodyNode {
        val instantiationData = when (nodeExpression) {
            is FunctionExpressionInstantiationNode -> {
                programContext.buildModuleInvocationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )
            }
            is FunctionExpressionReferenceNode -> {
                val parameterValue = parameterValuesContext[nodeExpression.identifier.value]!!

                if (parameterValue is FunctionInstantiationParameterValue) {
                    parameterValue.value
                } else {
                    throw Exception("Expected module instantiation")
                }
            }
        }

        return createNodeFromFunctionInvocation(nodeIdentifier, instantiationData)
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