package com.uabutler.gaplir.builder

import com.uabutler.util.Named
import com.uabutler.ast.node.functions.FunctionIONode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.builder.util.*
import com.uabutler.gaplir.node.*
import com.uabutler.gaplir.node.input.*
import com.uabutler.gaplir.node.output.NodeOutputInterface
import com.uabutler.gaplir.node.output.NodeOutputRecordInterface
import com.uabutler.gaplir.node.output.NodeOutputVectorInterface
import com.uabutler.gaplir.node.output.NodeOutputWireInterface
import com.uabutler.gaplir.util.ModuleInvocation

class NodeBuilder(
    val programContext: ProgramContext,
    val moduleInstantiationTracker: ModuleInstantiationTracker
) {

    data class NodeBuildResult(
        val nodes: List<Node>,
        val moduleInstantiations: List<ModuleInstantiationTracker.ModuleInstantiationData>,
    )

    companion object {
        private fun getCircuitExpressionsFromCircuitStatement(
            circuitStatementNode: CircuitStatementNode,
            parameterValuesContext: Map<String, ParameterValue<*>>,
        ): List<CircuitExpressionNode> {
            return when (circuitStatementNode) {
                is NonConditionalCircuitStatementNode -> listOf(circuitStatementNode.statement)
                is ConditionalCircuitStatementNode -> {
                    val predicateValue = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                        staticExpression = circuitStatementNode.predicate,
                        context = parameterValuesContext,
                    )

                    if (predicateValue != 0) {
                        circuitStatementNode.ifBody.flatMap {
                            getCircuitExpressionsFromCircuitStatement(
                                circuitStatementNode = it,
                                parameterValuesContext = parameterValuesContext,
                            )
                        }
                    } else {
                        circuitStatementNode.elseBody.flatMap {
                            getCircuitExpressionsFromCircuitStatement(
                                circuitStatementNode = it,
                                parameterValuesContext = parameterValuesContext,
                            )
                        }
                    }
                }
            }
        }

        fun <K, V> flattenMap(list: List<Map<K, V>>): Map<K, V> =
            mutableMapOf<K, V>().apply {
                for (innerMap in list) putAll(innerMap)
            }
    }

    sealed class Projection

    data object WholeInterfaceProjection: Projection()

    data class VectorSliceProjection(
        val startIndex: Int,
        val endIndex: Int,
    ): Projection()

    data class NodeInputInterfaceProjection(
        val input: NodeInputInterface,
        val projection: Projection = WholeInterfaceProjection,
    )

    data class NodeOutputInterfaceProjection(
        val output: NodeOutputInterface,
        val projection: Projection = WholeInterfaceProjection,
    )

    data class GeneratedNodes(
        val declaredNodes: Map<String, Node> = emptyMap(),
        val anonymousNodes: Collection<Node> = emptyList(),
    )

    data class CircuitExpressionResult(
        val inputs: List<NodeInputInterfaceProjection>,
        val outputs: List<NodeOutputInterfaceProjection>,
        val generatedNodes: GeneratedNodes = GeneratedNodes(),
    )

    private fun createNodeFromFunctionInvocation(
        invocationName: String,
        instantiationData: ModuleInstantiationTracker.ModuleInstantiationData
    ): Node {
        // First, search predefined functions. If there is no predefined function with this name, search defined functions
        val matchingPredefinedFunction = PredefinedFunction.searchPredefinedFunctions(instantiationData)

        if (matchingPredefinedFunction != null) {
            return PredefinedFunctionInvocationNode(
                invocationName = invocationName,
                predefinedFunction = matchingPredefinedFunction,
            )
        } else {
            // Find the matching defined function
            val instantiation = moduleInstantiationTracker.visitModule(instantiationData)

            return ModuleInvocationNode(
                invokedModuleName = invocationName,
                moduleInvocation = ModuleInvocation(
                    gaplFunctionName = instantiationData.functionIdentifier,
                ),
                functionInputInterfaces = instantiation.input.map { Named(it.key, it.value) },
                functionOutputInterfaces = instantiation.output.map { Named(it.key, it.value) },
            )
        }
    }

    fun processCircuitNodeExpressionNode(
        nodeExpression: CircuitNodeExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
        existingDeclaredNodes: Map<String, Node>
    ): CircuitExpressionResult {
        when (nodeExpression) {

            is DeclaredInterfaceCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value

                val interfaceStructure = programContext.buildInterfaceWithContext(
                    node = nodeExpression.type,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = PassThroughNode(
                    interfaceStructures = listOf(Named(identifier, interfaceStructure)),
                )

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it.item) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it.item) },
                    generatedNodes = GeneratedNodes(
                        declaredNodes = mapOf(identifier to node),
                    ),
                )
            }

            is DeclaredFunctionCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value

                val instantiationData = programContext.buildModuleInstantiationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createNodeFromFunctionInvocation(identifier, instantiationData)

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it.item) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it.item) },
                    generatedNodes = GeneratedNodes(
                        declaredNodes = mapOf(identifier to node),
                    )
                )
            }

            is AnonymousFunctionCircuitExpressionNode -> {
                val identifier = AnonymousIdentifierGenerator.genIdentifier()

                val instantiationData = programContext.buildModuleInstantiationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createNodeFromFunctionInvocation(identifier, instantiationData)

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it.item) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it.item) },
                    generatedNodes = GeneratedNodes(
                        anonymousNodes = listOf(node)
                    )
                )
            }

            is ReferenceCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value
                val referencedNode = existingDeclaredNodes[identifier]!!

                // We should be referencing a declared node, which has a single interface
                val input = referencedNode.inputs.firstOrNull()?.let { input ->
                    nodeExpression.singleAccesses.fold(input.item) { currentInput, accessNode ->
                        when (accessNode) {
                            is MemberAccessOperationNode -> {
                                assert(currentInput is NodeInputRecordInterface)
                                (currentInput as NodeInputRecordInterface).ports[accessNode.memberIdentifier.value]!!
                            }
                            is SingleArrayAccessOperationNode -> {
                                val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                                    staticExpression = accessNode.index,
                                    context = parameterValuesContext,
                                )
                                assert(currentInput is NodeInputVectorInterface)
                                (currentInput as NodeInputVectorInterface).vector[index]
                            }
                        }
                    }
                }

                val output = referencedNode.outputs.firstOrNull()?.let { output ->
                    nodeExpression.singleAccesses.fold(output.item) { currentOutput, accessNode ->
                        when (accessNode) {
                            is MemberAccessOperationNode -> {
                                assert(currentOutput is NodeOutputRecordInterface)
                                (currentOutput as NodeOutputRecordInterface).ports[accessNode.memberIdentifier.value]!!
                            }
                            is SingleArrayAccessOperationNode -> {
                                val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                                    staticExpression = accessNode.index,
                                    context = parameterValuesContext,
                                )
                                assert(currentOutput is NodeOutputVectorInterface)
                                (currentOutput as NodeOutputVectorInterface).vector[index]
                            }
                        }
                    }
                }

                val projection = nodeExpression.multipleAccess?.let {
                    VectorSliceProjection(
                        startIndex = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                            staticExpression = it.startIndex,
                            context = parameterValuesContext,
                        ),
                        endIndex = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                            staticExpression = it.endIndex,
                            context = parameterValuesContext,
                        ),
                    )
                } ?: WholeInterfaceProjection

                return CircuitExpressionResult(
                    inputs = listOfNotNull(
                        input?.let {
                            NodeInputInterfaceProjection(
                                input = it,
                                projection = projection,
                            )
                        },
                    ),
                    outputs = listOfNotNull(
                        output?.let {
                            NodeOutputInterfaceProjection(
                                output = it,
                                projection = projection,
                            )
                        },
                    ),
                )
            }

            is RecordInterfaceConstructorExpressionNode -> TODO()

            is CircuitExpressionNodeCircuitExpression -> TODO()
        }
    }

    fun processCircuitGroupExpressionNode(
        groupExpression: CircuitGroupExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
        existingDeclaredNodes: Map<String, Node>
    ): CircuitExpressionResult {
        val results = groupExpression.expressions.map { node ->
            processCircuitNodeExpressionNode(node, interfaceValuesContext, parameterValuesContext, existingDeclaredNodes)
        }

        return CircuitExpressionResult(
            inputs = results.flatMap { it.inputs },
            outputs = results.flatMap { it.outputs },
            generatedNodes = GeneratedNodes(
                declaredNodes = flattenMap(results.map { it.generatedNodes.declaredNodes }),
                anonymousNodes = results.flatMap { it.generatedNodes.anonymousNodes },
            ),
        )
    }

    fun createConnections(
        outputOfCurrent: List<NodeOutputInterfaceProjection>,
        inputOfNext: List<NodeInputInterfaceProjection>,
    ) {
        assert(outputOfCurrent.size == inputOfNext.size)
        outputOfCurrent.zip(inputOfNext).forEach { (output, input) ->
            when (input.input) {
                is NodeInputWireInterface -> {
                    // Only whole projections supported
                    assert(input.projection is WholeInterfaceProjection && output.projection is WholeInterfaceProjection)
                    // We're not assigning to this twice
                    assert(input.input.input == null)
                    // Make sure structures match
                    assert(output.output is NodeOutputWireInterface)

                    // The actual setting
                    input.input.input = output.output as NodeOutputWireInterface
                }

                is NodeInputRecordInterface -> {
                    // Only whole projections supported
                    assert(input.projection is WholeInterfaceProjection && output.projection is WholeInterfaceProjection)
                    // We're not assigning to this twice
                    assert(input.input.input == null)
                    // TODO: Make sure the structures match

                    // The actual settings
                    input.input.input = output.output as NodeOutputRecordInterface
                }

                is NodeInputVectorInterface -> {
                    // Make sure nothing is being assigned to twice
                    // Specifically, make sure the input projection doesn't overlap with any of the existing connections
                    if (input.projection is WholeInterfaceProjection) {
                        assert(input.input.connections.isEmpty())
                    } else {
                        input.input.connections.forEach {
                            assert(it.destSlice !is WholeVector)

                            val projStart = (input.projection as VectorSliceProjection).startIndex
                            val projEnd = input.projection.endIndex

                            val connStart = (it.destSlice as VectorSlice).startIndex
                            val connEnd = it.destSlice.endIndex

                            assert(projStart > connEnd || connStart > projEnd)
                        }
                    }

                    // TODO: Make sure the structures match

                    // Compute source slice
                    val sourceSlice = if (output.projection is VectorSliceProjection) {
                        VectorSlice(output.projection.startIndex, output.projection.endIndex)
                    } else {
                        WholeVector
                    }

                    // Compute dest slice
                    val destSlice = if (input.projection is VectorSliceProjection) {
                        VectorSlice(input.projection.startIndex, input.projection.endIndex)
                    } else {
                        WholeVector
                    }

                    // Create connection
                    val newConnection = VectorConnection(
                        sourceVector = output.output as NodeOutputVectorInterface,
                        sourceSlice = sourceSlice,
                        destSlice = destSlice,
                    )

                    input.input.connections.add(newConnection)
                }
            }
        }
    }

    fun processCircuitConnectionExpressionNode(
        connectionExpression: CircuitConnectionExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
        existingDeclaredNodes: Map<String, Node>
    ): GeneratedNodes {
        val allDeclaredNodes = existingDeclaredNodes.toMutableMap()
        val newDeclaredNodes = mutableMapOf<String, Node>()
        val anonymousNodes = mutableListOf<Node>()

        // Step 1: Process each group
        val groups = connectionExpression.connectedExpression.asSequence()
            .map { processCircuitGroupExpressionNode(it, interfaceValuesContext, parameterValuesContext, allDeclaredNodes) }
            .onEach { newDeclaredNodes += it.generatedNodes.declaredNodes }
            .onEach { allDeclaredNodes += it.generatedNodes.declaredNodes }
            .onEach { anonymousNodes += it.generatedNodes.anonymousNodes }
            .toList()

        // Step 2: Form the connections between groups
        groups.zipWithNext().forEach {
            createConnections(
                outputOfCurrent = it.first.outputs,
                inputOfNext = it.second.inputs,
            )
        }

        return GeneratedNodes(
            declaredNodes = newDeclaredNodes,
            anonymousNodes = anonymousNodes,
        )
    }

    fun buildInputNodes(
        astNodes: List<FunctionIONode>,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): List<ModuleInputNode> {
        return astNodes.map {
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = it.interfaceType,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            ModuleInputNode(
                name = it.identifier.value,
                inputInterfaceStructure = interfaceStructure,
            )
        }
    }

    fun buildOutputNodes(
        astNodes: List<FunctionIONode>,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): List<ModuleOutputNode> {
        return astNodes.map {
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = it.interfaceType,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            ModuleOutputNode(
                name = it.identifier.value,
                outputInterfaceStructure = interfaceStructure,
            )
        }
    }

    // TODO FUNCTIONAL
    fun buildBodyNodes(
        astStatements: List<CircuitStatementNode>,
        inputNodes: List<ModuleInputNode>,
        outputNodes: List<ModuleOutputNode>,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): NodeBuildResult {

        // Step 1: Let's simplify this a bit by evaluating all the conditionals
        val circuitExpressions = astStatements.flatMap {
            getCircuitExpressionsFromCircuitStatement(
                circuitStatementNode = it,
                parameterValuesContext = parameterValuesContext,
            )
        }

        val ioNodes = inputNodes.associateBy { it.name } + outputNodes.associateBy { it.name }
        val allDeclaredNodes = ioNodes.toMutableMap()
        val anonymousNodes = mutableListOf<Node>()

        // Step 2: Process the expressions to create nodes and connect them
        val results = circuitExpressions.asSequence()
            .map { processCircuitConnectionExpressionNode(it as CircuitConnectionExpressionNode, interfaceValuesContext, parameterValuesContext, allDeclaredNodes) }
            .onEach { allDeclaredNodes += it.declaredNodes }
            .onEach { anonymousNodes += it.anonymousNodes }
            .toList()

        return NodeBuildResult(
            nodes = results.flatMap { it.declaredNodes.values } + results.flatMap { it.anonymousNodes },
            moduleInstantiations = emptyList() // TODO: Once we handle function calls
        )
    }

}