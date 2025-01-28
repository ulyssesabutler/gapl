package com.uabutler.v2.gaplir.builder

import com.uabutler.util.StringGenerator
import com.uabutler.v2.ast.node.functions.FunctionIONode
import com.uabutler.v2.ast.node.functions.circuits.*
import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.builder.util.ModuleInstantiationTracker
import com.uabutler.v2.gaplir.builder.util.ProgramContext
import com.uabutler.v2.gaplir.builder.util.StaticExpressionEvaluator
import com.uabutler.v2.gaplir.node.ModuleInputNode
import com.uabutler.v2.gaplir.node.ModuleOutputNode
import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.node.PassThroughNode
import com.uabutler.v2.gaplir.node.input.*
import com.uabutler.v2.gaplir.node.output.NodeOutputInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputRecordInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputVectorInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputWireInterface

class NodeBuilder(val programContext: ProgramContext) {

    data class NodeBuildResult(
        val nodes: List<Node>,
        val moduleInstantiations: List<ModuleInstantiationTracker.ModuleInstantiationData>,
    )

    companion object {
        private fun getCircuitExpressionsFromCircuitStatement(
            circuitStatementNode: CircuitStatementNode,
            parameterValuesContext: Map<String, Int>, // TODO: This could be any value
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

    fun processCircuitNodeExpressionNode(
        nodeExpression: CircuitNodeExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
        declaredNodes: Map<String, Node>
    ): CircuitExpressionResult {
        when (nodeExpression) {
            is AnonymousNodeCircuitExpressionNode -> {
                val interfaceStructure = programContext.buildInterfaceWithContext(
                    node = nodeExpression.type,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = PassThroughNode(
                    interfaceStructures = listOf(interfaceStructure),
                )

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it) },
                    generatedNodes = GeneratedNodes(
                        anonymousNodes = listOf(node),
                    ),
                )
            }

            is DeclaredNodeCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value
                // TODO: I think functions calls will get mapped here, and should be handled
                // TODO: Add the function call to the module instantiations

                val interfaceStructure = programContext.buildInterfaceWithContext(
                    node = nodeExpression.type,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = PassThroughNode(
                    name = identifier,
                    interfaceStructures = listOf(interfaceStructure),
                )

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it) },
                    generatedNodes = GeneratedNodes(
                        declaredNodes = mapOf(identifier to node),
                    ),
                )
            }

            is IdentifierCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value

                // This identifier could either be another declared node, or it could be a generic
                declaredNodes[identifier]?.let {
                    return CircuitExpressionResult(
                        inputs = it.inputs.map { NodeInputInterfaceProjection(it) },
                        outputs = it.outputs.map { NodeOutputInterfaceProjection(it) },
                    )
                }

                val interfaceStructure = interfaceValuesContext[identifier]!!

                val node = PassThroughNode(
                    interfaceStructures = listOf(interfaceStructure)
                )

                return CircuitExpressionResult(
                    inputs = node.inputs.map { NodeInputInterfaceProjection(it) },
                    outputs = node.outputs.map { NodeOutputInterfaceProjection(it) },
                    generatedNodes = GeneratedNodes(
                        anonymousNodes = listOf(node),
                    ),
                )
            }

            is ReferenceCircuitExpressionNode -> {
                val identifier = nodeExpression.identifier.value
                val referencedNode = declaredNodes[identifier]!!

                // We should be referencing a declared node, which has a single interface
                assert(referencedNode.inputs.size == 1)
                val input = referencedNode.inputs.first()

                assert(referencedNode.outputs.size == 1)
                val output = referencedNode.outputs.first()

                var currentInput = input
                nodeExpression.singleAccesses.forEach {
                    when (it) {
                        is MemberAccessOperationNode -> {
                            assert(currentInput is NodeInputRecordInterface)
                            currentInput = (currentInput as NodeInputRecordInterface).ports[it.memberIdentifier.value]!!
                        }
                        is SingleArrayAccessOperationNode -> {
                            val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                                staticExpression = it.index,
                                context = parameterValuesContext,
                            )
                            assert(currentInput is NodeInputVectorInterface)
                            currentInput = (currentInput as NodeInputVectorInterface).vector[index]
                        }
                    }
                }

                var currentOutput = output
                nodeExpression.singleAccesses.forEach {
                    when (it) {
                        is MemberAccessOperationNode -> {
                            assert(currentOutput is NodeOutputRecordInterface)
                            currentOutput = (currentOutput as NodeOutputRecordInterface).ports[it.memberIdentifier.value]!!
                        }
                        is SingleArrayAccessOperationNode -> {
                            val index = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                                staticExpression = it.index,
                                context = parameterValuesContext,
                            )
                            assert(currentOutput is NodeOutputVectorInterface)
                            currentOutput = (currentOutput as NodeOutputVectorInterface).vector[index]
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
                    inputs = listOf(
                        NodeInputInterfaceProjection(
                            input = currentInput,
                            projection = projection,
                        )
                    ),
                    outputs = listOf(
                        NodeOutputInterfaceProjection(
                            output = currentOutput,
                            projection = projection,
                        )
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
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
        declaredNodes: Map<String, Node>
    ): CircuitExpressionResult {
        val results = groupExpression.expressions.map { node ->
            processCircuitNodeExpressionNode(node, interfaceValuesContext, parameterValuesContext, declaredNodes)
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

                            val proj_start = (input.projection as VectorSliceProjection).startIndex
                            val proj_end = (input.projection as VectorSliceProjection).endIndex

                            val conn_start = (it.destSlice as VectorSlice).startIndex
                            val conn_end = (it.destSlice as VectorSlice).endIndex

                            assert(proj_start > conn_end || conn_start > proj_end)
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
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
        declaredNodes: Map<String, Node>
    ): GeneratedNodes {
        val declaredNodes = declaredNodes.toMutableMap()
        val anonymousNodes = mutableListOf<Node>()

        // Step 1: Process each group
        val groups = connectionExpression.connectedExpression.asSequence()
            .map { processCircuitGroupExpressionNode(it, interfaceValuesContext, parameterValuesContext, declaredNodes) }
            .onEach { declaredNodes += it.generatedNodes.declaredNodes }
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
            declaredNodes = declaredNodes,
            anonymousNodes = anonymousNodes,
        )
    }

    fun buildInputNodes(
        astNodes: List<FunctionIONode>,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
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
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
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

    fun buildBodyNodes(
        astStatements: List<CircuitStatementNode>,
        inputNodes: List<ModuleInputNode>,
        outputNodes: List<ModuleOutputNode>,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, Int>, // TODO: This could be any value
    ): NodeBuildResult {

        // Step 1: Let's simplify this a bit by evaluating all the conditionals
        val circuitExpressions = astStatements.flatMap {
            getCircuitExpressionsFromCircuitStatement(
                circuitStatementNode = it,
                parameterValuesContext = parameterValuesContext,
            )
        }

        val ioNodes = inputNodes.associateBy { it.name } + outputNodes.associateBy { it.name }
        val declaredNodes = ioNodes.toMutableMap()
        val anonymousNodes = mutableListOf<Node>()

        // Step 2: Process the expressions to create nodes and connect them
        val results = circuitExpressions.asSequence()
            .map { processCircuitConnectionExpressionNode(it as CircuitConnectionExpressionNode, interfaceValuesContext, parameterValuesContext, declaredNodes) }
            .onEach { declaredNodes += it.declaredNodes }
            .onEach { anonymousNodes += it.anonymousNodes }
            .toList()

        return NodeBuildResult(
            nodes = results.flatMap { it.declaredNodes.values } + results.flatMap { it.anonymousNodes },
            moduleInstantiations = emptyList() // TODO: Once we handle function calls
        )
    }

}