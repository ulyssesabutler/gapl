package com.uabutler.netlistir.builder

import com.uabutler.ast.node.ProtocolAnnotationNode
import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.diagnostics.DiagnosticsCollector
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.netlistir.builder.util.*
import com.uabutler.netlistir.netlist.*
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.PredefinedFunction
import com.uabutler.netlistir.util.IslandControlFunction
import com.uabutler.netlistir.util.LazyForkFunction
import com.uabutler.netlistir.util.ValidDelayFunction
import com.uabutler.util.AnonymousIdentifierGenerator
import com.uabutler.util.PredefinedFunctionNames
import com.uabutler.util.Protocol
import java.math.BigInteger

class NodeBuilder(
    val programContext: ProgramContext,
    val moduleInstantiationTracker: ModuleInstantiationTracker,
    val module: MutableModule,
    val functionDefinitionAstNode: FunctionDefinitionNode,
    val interfaceValuesContext: Map<String, InterfaceStructure>,
    val parameterValuesContext: Map<String, ParameterValue<*>>,
    val diagnosticsCollector: DiagnosticsCollector,
) {
    val inputAstNodes = functionDefinitionAstNode.inputFunctionIO
    val outputAstNodes = functionDefinitionAstNode.outputFunctionIO
    val bodyAstNodes = functionDefinitionAstNode.statements

    val netlistNodes = mutableMapOf<String, Node>()

    // Declaration span of each node, recorded at creation time - used to give undriven-wire
    // diagnostics a precise location without storing anything on the Node/Module IR types
    // themselves (which would go stale once transformers start relocating nodes across modules).
    // Not private: ModuleBuilder pulls this out after buildNodesIntoModule() finishes and merges
    // it into a program-wide map, since the combinational-loop check runs across every built
    // module, after this NodeBuilder instance is otherwise done.
    val nodeSpans = mutableMapOf<Node, SourceSpan>()

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

    private val functionName = functionDefinitionAstNode.identifier.value
    private val protocol = functionDefinitionAstNode.protocol

    /**
     * Every island this function creates - a call coercing a @pure callee into this function's
     * protocol - with its call site. Islands must be register-free, which can only be checked once the
     * callee (and everything it calls) is built, so ModuleBuilder checks these after the build loop.
     */
    val islands = mutableListOf<Pair<Module.Invocation, SourceSpan>>()

    fun buildNodesIntoModule() {
        diagnosticsBeforeBuild = diagnosticsCollector.diagnostics().size
        validateInputCount()
        buildInputNodes()
        buildOutputNodes()
        buildHandshakePorts()
        buildBodyNodes()
        wireChannels()
        validateWiresConnected()
    }

    /*
     * Layer-3 handshake plumbing for a protocol'd body (type-system.md §5). A *channel* is an output group
     * of an input port or a call: it drives one `valid` and, under @ready_valid, listens to one `ready`. A
     * *sink* is a call's input or an output port: it takes a `valid` and, under @ready_valid, drives a
     * `ready`. During the build each call records which of its groups are sinks and sources (see
     * recordChannels); once the body is built, wireChannels inserts each island's control, traces every
     * sink back to the channel(s) feeding it through declared nodes, and wires the handshake:
     *
     * - a sink fed by more than one channel is a join written as plain wiring, which only an island may do;
     * - under @ready_valid a channel must reach exactly one sink: more is an implicit fork (forks must be
     *   explicit), none leaves its `ready` undriven. Under @valid there is no `ready`, so a channel may
     *   reach any number of sinks, including none.
     */

    private var diagnosticsBeforeBuild = 0

    private class ChannelSource(val description: String, val span: SourceSpan, val valid: OutputWire, val ready: InputWire?)
    private class ChannelSink(val groups: List<InputWireVectorGroup>, val valids: List<InputWire>, val ready: OutputWire?, val description: String)

    /** Every channel's handshake, by the output group carrying its data. */
    private val channelSources = mutableMapOf<OutputWireVectorGroup, ChannelSource>()
    private val channelSinks = mutableListOf<ChannelSink>()

    // An island's data groups and call site. `calleeValids` is set for a @valid callee inside @ready_valid
    // (coercion C): the callee's own valid ports, by data group name.
    private class ChannelIsland(
        val inputs: List<InputWireVectorGroup>,
        val outputs: List<OutputWireVectorGroup>,
        val span: SourceSpan,
        val calleeValids: Map<String, WireVectorGroup<*>>? = null,
    )
    private val channelIslands = mutableListOf<ChannelIsland>()

    // fork_lazy calls: the input group and the branch groups.
    private class LazyFork(val input: InputWireVectorGroup, val branches: List<OutputWireVectorGroup>, val span: SourceSpan)
    private val lazyForks = mutableListOf<LazyFork>()

    // The single wire of a one-bit control port, by IO index.
    private fun inputBit(node: Node, index: Int): InputWire = node.inputWireVectorGroups[index].wires().single()
    private fun outputBit(node: Node, index: Int): OutputWire = node.outputWireVectorGroups[index].wires().single()

    // The function's own handshake ports (HandshakePorts), created in the same order as its signature.
    private fun buildHandshakePorts() {
        if (protocol == Protocol.PURE) return
        val readyValid = protocol == Protocol.READY_VALID

        fun inputPort(name: String, span: SourceSpan) =
            createInputNode(name) { node -> listOf(buildOutputWireVectorGroups(WireInterfaceStructure, node)) }.also { nodeSpans[it] = span }
        fun outputPort(name: String, span: SourceSpan) =
            createOutputNode(name) { node -> listOf(buildInputWireVectorGroups(WireInterfaceStructure, node)) }.also { nodeSpans[it] = span }

        val inputValids = inputAstNodes.associate { it.identifier.value to inputPort(HandshakePorts.valid(it.identifier.value), it.span) }
        val outputValids = outputAstNodes.associate { it.identifier.value to outputPort(HandshakePorts.valid(it.identifier.value), it.span) }
        val outputReadys = if (readyValid) outputAstNodes.associate { it.identifier.value to inputPort(HandshakePorts.ready(it.identifier.value), it.span) } else emptyMap()
        val inputReadys = if (readyValid) inputAstNodes.associate { it.identifier.value to outputPort(HandshakePorts.ready(it.identifier.value), it.span) } else emptyMap()

        inputAstNodes.forEach { astNode ->
            val name = astNode.identifier.value
            channelSources[module.getInputNode(name).outputWireVectorGroups.single()] = ChannelSource(
                description = name,
                span = astNode.span,
                valid = inputValids.getValue(name).outputWires().single(),
                ready = inputReadys[name]?.inputWires()?.single(),
            )
        }
        outputAstNodes.forEach { astNode ->
            val name = astNode.identifier.value
            channelSinks += ChannelSink(
                groups = module.getOutputNode(name).inputWireVectorGroups,
                valids = listOf(outputValids.getValue(name).inputWires().single()),
                ready = outputReadys[name]?.outputWires()?.single(),
                description = "output '$name'",
            )
        }
    }

    private fun wireChannels() {
        if (protocol == Protocol.PURE) return

        channelIslands.forEach { island ->
            when (protocol) {
                Protocol.VALID -> wireValidIsland(island)
                else -> wireReadyValidIsland(island)
            }
        }
        lazyForks.forEach { fork ->
            val control = createHandshakeNode(LazyForkFunction(fork.branches.size), fork.span)
            channelSinks += ChannelSink(listOf(fork.input), listOf(inputBit(control, 0)), outputBit(control, 0), "the input of fork_lazy")
            fork.branches.forEachIndexed { k, branch ->
                channelSources[branch] = ChannelSource(describe(branch), fork.span, outputBit(control, 1 + k), inputBit(control, 1 + k))
            }
        }

        val consumers = mutableMapOf<ChannelSource, MutableList<ChannelSink>>()
        val joined = mutableSetOf<ChannelSource>() // already reported as part of a join: not also "unconsumed"
        channelSinks.forEach { sink ->
            val sources = sink.groups
                .flatMap { it.wires() }
                .mapNotNull { sourceChannelOf(it) }
                .mapNotNull { channelSources[it] }
                .distinctBy { it.valid } // one island's outputs share a valid under @valid: not a join

            when {
                sources.size > 1 -> {
                    diagnosticsCollector.reportError(
                        BuilderDiagnosticKind.ImplicitJoin(functionName, sink.description, sources.size),
                        nodeSpans[sink.groups.first().parentNode] ?: functionDefinitionAstNode.span,
                    )
                    joined += sources
                }
                sources.size == 1 -> {
                    sink.valids.forEach { module.connect(it as InputWire, sources.single().valid) }
                    consumers.getOrPut(sources.single()) { mutableListOf() } += sink
                }
                // No source: the data itself is undriven, which validateWiresConnected reports.
            }
        }

        if (protocol != Protocol.READY_VALID) return
        // A call already reported as illegal records no sinks, so its input channels would look unconsumed.
        val hasEarlierErrors = diagnosticsCollector.diagnostics().size > diagnosticsBeforeBuild
        channelSources.values.distinct().filter { it !in joined }.forEach { source ->
            val sinks = consumers[source].orEmpty()
            when (sinks.size) {
                0 -> if (!hasEarlierErrors) diagnosticsCollector.reportError(BuilderDiagnosticKind.UnconsumedChannel(functionName, source.description), source.span)
                1 -> module.connect(source.ready!!, sinks.single().ready!!)
                else -> diagnosticsCollector.reportError(
                    BuilderDiagnosticKind.ImplicitFork(functionName, source.description, sinks.size), source.span,
                )
            }
        }
    }

    // A @valid island's valid passes through a ValidDelayFunction - depth 0 until retiming sets it. An
    // island with no inputs (a constant) is always valid.
    private fun wireValidIsland(island: ChannelIsland) {
        // More than one input is a join, already reported by validateNoJoin - nothing to wire.
        if (island.inputs.size > 1) return

        val delay = createHandshakeNode(ValidDelayFunction(depth = 0), island.span)
        val valid = delay.outputWires().single()
        island.outputs.forEach { channelSources[it] = ChannelSource(describe(it), island.span, valid, ready = null) }

        if (island.inputs.isEmpty()) {
            val one = createHandshakeNode(LiteralFunction(1, BigInteger.ONE), island.span)
            module.connect(delay.inputWires().single(), one.outputWires().single())
        } else {
            channelSinks += ChannelSink(island.inputs, listOf(delay.inputWires().single()), ready = null, description = "an island's input")
        }
    }

    // A @ready_valid island's control (IslandControlFunction): a join over its inputs, an eager fork over its
    // outputs. For a @valid callee (coercion C) the callee's own valids also see the input's valid, and the
    // control learns which outputs it produced from the callee's output valids.
    private fun wireReadyValidIsland(island: ChannelIsland) {
        val function = IslandControlFunction(island.inputs.size, island.outputs.size, producedByCallee = island.calleeValids != null)
        val control = createHandshakeNode(function, island.span)

        island.inputs.forEachIndexed { i, input ->
            val calleeValid = island.calleeValids?.get(HandshakePorts.valid(input.identifier))?.wires()?.single()
            channelSinks += ChannelSink(
                groups = listOf(input),
                valids = listOfNotNull(inputBit(control, function.inValid(i)), calleeValid as InputWire?),
                ready = outputBit(control, function.inReady(i)),
                description = "an island's input",
            )
        }
        island.outputs.forEachIndexed { o, output ->
            island.calleeValids?.let { valids ->
                module.connect(inputBit(control, function.produced(o)), valids.getValue(HandshakePorts.valid(output.identifier)).wires().single() as OutputWire)
            }
            channelSources[output] = ChannelSource(
                description = describe(output),
                span = island.span,
                valid = outputBit(control, function.outValid(o)),
                ready = inputBit(control, function.outReady(o)),
            )
        }
    }

    private fun describe(group: OutputWireVectorGroup) = "${group.parentNode.name()}.${group.identifier}"

    // The channel (output group) driving this wire, looking back through declared nodes, which are just
    // names for wires; null if the wire is undriven.
    private fun sourceChannelOf(wire: InputWire): OutputWireVectorGroup? {
        var current = wire
        while (true) {
            val source = module.getConnectionForInputWireOrNull(current)?.source ?: return null
            val node = source.parentWireVector.parentGroup.parentNode
            if (node !is PassThroughNode || node in loweredCallOutputs) {
                return source.parentWireVector.parentGroup as OutputWireVectorGroup
            }
            val index = node.outputWires().indexOf(source)
            current = node.inputWires()[index]
        }
    }

    private fun createHandshakeNode(function: PredefinedFunction, span: SourceSpan) = createPredefinedFunctionNode(
        identifier = AnonymousIdentifierGenerator.genIdentifier(),
        inputWireVectorGroupsBuilder = { node -> function.inputs.map { it.toInputWireVectorGroup(node) } },
        outputWireVectorGroupsBuilder = { node -> function.outputs.map { it.toOutputWireVectorGroup(node) } },
        predefinedFunction = function,
    ).also { nodeSpans[it] = span }

    // A @valid function fires on its one input; firing on several would be a join.
    private fun validateInputCount() {
        if (protocol == Protocol.VALID && inputAstNodes.size > 1) {
            diagnosticsCollector.reportError(
                BuilderDiagnosticKind.ValidFunctionHasMultipleInputs(functionName, inputAstNodes.size),
                functionDefinitionAstNode.span,
            )
        }
    }


    // A module's own input ports are driven by the caller, so only OutputNodes and body nodes
    // (whose input wires all need a source from within this module) can be undriven.
    private fun validateWiresConnected() {
        // Handshake ports are wired by the compiler, never by the user, so an undriven one is only ever a
        // consequence of an error already reported (an illegal call, undriven data). Report them only if
        // nothing else has been - then it would be a compiler bug, better surfaced than left to crash later.
        fun isHandshakePort(node: Node, group: InputWireVectorGroup) =
            HandshakePorts.isHandshakePort(group.identifier) ||
                (node is OutputNode && HandshakePorts.isHandshakePort(node.name())) ||
                (node is PredefinedFunctionNode && node.predefinedFunction.let {
                    it is ValidDelayFunction || it is IslandControlFunction || it is LazyForkFunction
                })
        fun isUndriven(group: InputWireVectorGroup) = group.wires().any { module.getConnectionForInputWireOrNull(it) == null }
        val skipHandshakePorts = diagnosticsCollector.diagnostics().isNotEmpty() || module.getNodes().any { node ->
            node.inputWireVectorGroups.any { !isHandshakePort(node, it) && isUndriven(it) }
        }

        module.getNodes().forEach { node ->
            val undrivenGroup = node.inputWireVectorGroups.firstOrNull { group ->
                !(skipHandshakePorts && isHandshakePort(node, group)) &&
                    group.wires().any { module.getConnectionForInputWireOrNull(it) == null }
            } ?: return@forEach

            val span = nodeSpans[node]!!
            val functionName = functionDefinitionAstNode.identifier.value

            when (node) {
                is OutputNode -> diagnosticsCollector.reportError(
                    BuilderDiagnosticKind.UndrivenOutputPort(node.name(), functionName),
                    span,
                )
                is BodyNode -> diagnosticsCollector.reportError(
                    BuilderDiagnosticKind.UndrivenNodeInput(
                        nodeName = node.name(),
                        nodeType = node.nodeType(),
                        inputName = undrivenGroup.identifier.takeIf { it != "only" },
                        functionName = functionName,
                    ),
                    span,
                )
                else -> {} // InputNode never has input wires - driven by the caller.
            }
        }
    }

    // MutableModule.connect() throws if the input wire already has a connection - checking first
    // lets us report a diagnostic and keep processing (first connection wins, like the rest of the
    // diagnostics pipeline) instead of crashing the whole compile on the second driver.
    private fun reportMultiplyDrivenInput(inputWire: InputWire, span: SourceSpan) {
        val group = inputWire.parentWireVector.parentGroup
        val node = group.parentNode
        val functionName = functionDefinitionAstNode.identifier.value

        when (node) {
            is OutputNode -> diagnosticsCollector.reportError(
                BuilderDiagnosticKind.MultiplyDrivenOutputPort(node.name(), functionName),
                span,
            )
            is BodyNode -> diagnosticsCollector.reportError(
                BuilderDiagnosticKind.MultiplyDrivenNodeInput(
                    nodeName = node.name(),
                    nodeType = node.nodeType(),
                    inputName = group.identifier.takeIf { it != "only" },
                    functionName = functionName,
                ),
                span,
            )
            else -> {} // InputNode never has input wires - driven by the caller.
        }
    }

    private fun buildInputNodes() {
        inputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            val node = createInputNode(
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
            nodeSpans[node] = astNode.span
        }
    }

    private fun buildOutputNodes() {
        outputAstNodes.map { astNode ->
            val interfaceStructure = programContext.buildInterfaceWithContext(
                node = astNode.interfaceExpression,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )

            val node = createOutputNode(
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
            nodeSpans[node] = astNode.span
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

                if (predicateValue != BigInteger.ZERO) {
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
                outputs = outputs.map { it.projection() }
            )
        }

    }

    /* Each circuit connection expression will be a list of circuit groups separated by the connection operator.
     * For example, we might have groups "a,b,c", "d,e", and "f". Then, a connection expression "a,b,c => d,e => f"
     *
     */
    private fun processCircuitConnectionExpressionNode(
        connectionExpression: CircuitConnectionExpressionNode,
    ): IOGroups {
        val groups = connectionExpression.connectedExpression.map { processCircuitGroupExpressionNode(it) }

        groups.zipWithNext()
            .map { (previous, current) -> previous.outputs to current.inputs }
            .forEach { (previousOutputs, currentInputs) ->
                createOutputWireVectorGroupConnections(
                    previousOutputs = previousOutputs,
                    currentInputs = currentInputs,
                    connectionExpression = connectionExpression,
                )
            }

        // A grouped (parenthesized) circuit expression is used as a single node elsewhere, so it
        // needs to expose its own boundary I/O: the first group's inputs and the last group's outputs.
        return IOGroups(
            inputs = groups.first().inputs,
            outputs = groups.last().outputs,
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
        connectionExpression: CircuitConnectionExpressionNode,
    ) {
        // TODO: This is a very lazy, overly simplistic validation.
        //   There are definition interface structures that will match here that shouldn't actually match.
        //   For example, wire[5][10] and wire[50].

        fun checkForMismatch(s1: Int, s2: Int) {
            if (s1 != s2) {
                // TODO: Also, this error handling is shit. Ideally, we would show the user which connection is mismatched.
                // previousOutputs/currentInputs can each be empty - not just a different size - when
                // one side is a function's own port referenced backwards (e.g. its output port used
                // as a source, or its input port used as a target): OutputNode/InputNode always
                // report zero wire vector groups on that side by construction (see Node.kt). Fall
                // back to a placeholder name instead of crashing on .first() of an empty list.
                val gaplModuleName = functionDefinitionAstNode.identifier.value
                val previousOutputNodeName = previousOutputs.firstOrNull()?.sourceGroup?.parentNode?.name() ?: "(nothing)"
                val currentInputNodeName = currentInputs.firstOrNull()?.sourceGroup?.parentNode?.name() ?: "(nothing)"

                throw BuilderDiagnosticException(
                    BuilderDiagnosticKind.WireCountMismatch(gaplModuleName, previousOutputNodeName, s1, currentInputNodeName, s2),
                    connectionExpression.span,
                )
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

        // A single connection statement can drive many wires at once (a whole multi-bit group),
        // so collapse conflicts down to one diagnostic per target group here, not one per wire -
        // otherwise a single "i => o;" repeated against a wide o would report once per bit.
        val alreadyReportedGroups = mutableSetOf<WireVectorGroup<*>>()

        wirePairs.forEach { (previousOutput, currentInput) ->
            val inputWire = currentInput as InputWire
            val outputWire = previousOutput as OutputWire

            if (module.getConnectionForInputWireOrNull(inputWire) != null) {
                val group = inputWire.parentWireVector.parentGroup
                if (alreadyReportedGroups.add(group)) {
                    reportMultiplyDrivenInput(inputWire, connectionExpression.span)
                }
            } else {
                module.connect(inputWire, outputWire)
            }
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
                nodeSpans[node] = nodeExpression.span

                IOGroups.fromWireVectorGroups(
                    inputs = node.inputWireVectorGroups,
                    outputs = node.outputWireVectorGroups,
                )
            }

            is AnonymousInterfaceCircuitExpressionNode -> {
                val structure = programContext.buildInterfaceWithContext(
                    node = nodeExpression.type,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                val node = createPassThroughNode(
                    identifier = AnonymousIdentifierGenerator.genIdentifier(),
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
                nodeSpans[node] = nodeExpression.span

                IOGroups.fromWireVectorGroups(
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

                invokeFunction(nodeExpression.identifier.value, instantiationData, nodeExpression.span, nodeExpression.instantiation.coercion)
            }

            is DeclaredGenericFunctionCircuitExpressionNode -> {
                val parameterValue = parameterValuesContext[nodeExpression.functionIdentifier.value]!!

                val instantiationData = if (parameterValue is FunctionInstantiationParameterValue) {
                    parameterValue.value
                } else {
                    throw BuilderDiagnosticException(
                        BuilderDiagnosticKind.ExpectedModuleInstantiation(nodeExpression.functionIdentifier.value),
                        nodeExpression.span,
                    )
                }

                invokeFunction(nodeExpression.identifier.value, instantiationData, nodeExpression.span, nodeExpression.coercion)
            }

            is AnonymousFunctionCircuitExpressionNode -> {
                val instantiationData = programContext.buildModuleInvocationDataWithContext(
                    node = nodeExpression.instantiation,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                )

                invokeFunction(AnonymousIdentifierGenerator.genIdentifier(), instantiationData, nodeExpression.span, nodeExpression.instantiation.coercion)
            }

            is AnonymousGenericFunctionCircuitExpressionNode -> {
                val parameterValue = parameterValuesContext[nodeExpression.functionIdentifier.value]!!

                val instantiationData = if (parameterValue is FunctionInstantiationParameterValue) {
                    parameterValue.value
                } else {
                    throw BuilderDiagnosticException(
                        BuilderDiagnosticKind.ExpectedModuleInstantiation(nodeExpression.functionIdentifier.value),
                        nodeExpression.span,
                    )
                }

                invokeFunction(AnonymousIdentifierGenerator.genIdentifier(), instantiationData, nodeExpression.span, nodeExpression.coercion)
            }

            is ReferenceCircuitExpressionNode -> {
                val name = nodeExpression.identifier.value
                val (inputWireVectorGroups, outputWireVectorGroups) = netlistNodes[name]
                    ?.let { it.inputWireVectorGroups to it.outputWireVectorGroups }
                    ?: loweredCalls[name]?.let { it.inputs to it.outputs }
                    ?: throw BuilderDiagnosticException(
                        BuilderDiagnosticKind.UnableToFindCircuitNode(name),
                        nodeExpression.span,
                    )

                val projectionInformation = getProjectionValues(nodeExpression.singleAccesses, nodeExpression.multipleAccess)
                val inputWireVectorGroup = inputWireVectorGroups.firstOrNull()
                val outputWireVectorGroup = outputWireVectorGroups.firstOrNull()

                IOGroups(
                    inputs = inputWireVectorGroup?.let { listOf(getInputWireVectorGroupProjection(it, projectionInformation)) } ?: emptyList(),
                    outputs = outputWireVectorGroup?.let { listOf(getOutputWireVectorGroupProjection(it, projectionInformation)) } ?: emptyList(),
                )
            }

            // TODO
            is ProtocolAccessorCircuitExpressionNode -> TODO()

            is RecordInterfaceConstructorExpressionNode -> TODO()

            is CircuitExpressionNodeCircuitExpression -> {
                processCircuitConnectionExpressionNode(nodeExpression.expression as CircuitConnectionExpressionNode)
            }

            is ErrorCircuitNodeExpressionNode -> throw IllegalStateException(
                "Reached NodeBuilder with an error node (${nodeExpression.message}) that should have been caught by semantic analysis - this is a compiler bug"
            )
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
            ).intValueExact()
            val end = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                staticExpression = multipleAccessNode.endIndex,
                context = parameterValuesContext,
            ).intValueExact()

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
                    ).intValueExact()

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

    /**
     * The boundary of a call lowered to wiring (view conversions, explicit forks): no single netlist node
     * has these groups, since project/overlay change structure and a PassThroughNode must keep it. Kept by
     * name so a declared call (`declare p: project(t, v)`) can still be referenced like any other node.
     */
    private data class LoweredCall(
        val inputs: List<InputWireVectorGroup>,
        val outputs: List<OutputWireVectorGroup>,
    )

    private val loweredCalls = mutableMapOf<String, LoweredCall>()

    // The pass-through nodes at a lowered call's boundary. Unlike a declared node, which is just a name for
    // wires, these stand in for a call - so the implicit-fork check treats them as a consumer and a channel.
    private val loweredCallInputs = mutableSetOf<Node>()
    private val loweredCallOutputs = mutableSetOf<Node>()

    private fun invokeFunction(
        invocationName: String,
        invocation: Module.Invocation,
        callSiteSpan: SourceSpan,
        coercion: ProtocolAnnotationNode?,
    ): IOGroups {
        val isIsland = validateCallProtocol(invocation, callSiteSpan, coercion)

        val lowered = when (PredefinedFunctionNames.from(invocation.gaplFunctionName)) {
            PredefinedFunctionNames.PROJECT -> lowerProject(invocation, callSiteSpan)
            PredefinedFunctionNames.OVERLAY -> lowerOverlay(invocation, callSiteSpan)
            PredefinedFunctionNames.ZEROS -> lowerZeros(invocation, callSiteSpan)
            PredefinedFunctionNames.FORK_EAGER, PredefinedFunctionNames.FORK_LAZY -> lowerFork(invocation, callSiteSpan)
            else -> {
                val node = createNodeFromFunctionInvocation(invocationName, invocation, callSiteSpan)
                // A protocol'd callee's handshake ports are wired by wireValidChannels, never by the user.
                val (inputs, inputHandshake) = node.inputWireVectorGroups.partition { !HandshakePorts.isHandshakePort(it.identifier) }
                val (outputs, outputHandshake) = node.outputWireVectorGroups.partition { !HandshakePorts.isHandshakePort(it.identifier) }

                validateNoJoin(invocation, inputs.size, callSiteSpan)
                recordChannels(invocation, isIsland, inputs, outputs, callSiteSpan, (inputHandshake + outputHandshake).associateBy { it.identifier })
                return IOGroups.fromWireVectorGroups(inputs = inputs, outputs = outputs)
            }
        }

        validateNoJoin(invocation, lowered.inputs.size, callSiteSpan)
        recordChannels(invocation, isIsland, lowered.inputs, lowered.outputs, callSiteSpan, handshake = emptyMap())
        loweredCalls[invocationName] = lowered
        loweredCallInputs += lowered.inputs.map { it.parentNode }
        loweredCallOutputs += lowered.outputs.map { it.parentNode }
        return IOGroups.fromWireVectorGroups(inputs = lowered.inputs, outputs = lowered.outputs)
    }

    /*
     * Records a call's part in a protocol'd body's channels: an island's groups are wired through its
     * control; a callee with this function's own protocol pairs each data group with its handshake ports
     * (`handshake`, by name); fork_eager is the identity island and fork_lazy has its own control.
     */
    private fun recordChannels(
        invocation: Module.Invocation,
        isIsland: Boolean,
        inputs: List<InputWireVectorGroup>,
        outputs: List<OutputWireVectorGroup>,
        callSiteSpan: SourceSpan,
        handshake: Map<String, WireVectorGroup<*>>,
    ) {
        if (protocol == Protocol.PURE) return
        val calleeName = invocation.gaplFunctionName
        val calleeProtocol = programContext.protocolOf(calleeName)

        when {
            isIsland -> channelIslands += ChannelIsland(inputs, outputs, callSiteSpan, calleeValids = handshake.takeIf { calleeProtocol == Protocol.VALID })
            calleeProtocol != protocol -> {} // an illegal call, already reported
            PredefinedFunctionNames.from(calleeName) == PredefinedFunctionNames.FORK_EAGER -> channelIslands += ChannelIsland(inputs, outputs, callSiteSpan)
            PredefinedFunctionNames.from(calleeName) == PredefinedFunctionNames.FORK_LAZY -> lazyForks += LazyFork(inputs.single(), outputs, callSiteSpan)
            else -> {
                inputs.forEach { group ->
                    channelSinks += ChannelSink(
                        groups = listOf(group),
                        valids = listOf(handshake.getValue(HandshakePorts.valid(group.identifier)).wires().single() as InputWire),
                        ready = handshake[HandshakePorts.ready(group.identifier)]?.wires()?.single() as OutputWire?,
                        description = "an input of '$calleeName'",
                    )
                }
                outputs.forEach { group ->
                    channelSources[group] = ChannelSource(
                        description = describe(group),
                        span = callSiteSpan,
                        valid = handshake.getValue(HandshakePorts.valid(group.identifier)).wires().single() as OutputWire,
                        ready = handshake[HandshakePorts.ready(group.identifier)]?.wires()?.single() as InputWire?,
                    )
                }
            }
        }
    }

    /*
     * The coercion matrix (type-system.md §5): a callee's protocol must be at or below the caller's. A
     * callee strictly below is an island, which must be marked with the caller's own protocol; a callee at
     * the same protocol is not an island, so marking it is an error.
     */
    private fun validateCallProtocol(invocation: Module.Invocation, callSiteSpan: SourceSpan, coercion: ProtocolAnnotationNode?): Boolean {
        val calleeName = invocation.gaplFunctionName
        val calleeProtocol = programContext.protocolOf(calleeName)

        when {
            calleeProtocol > protocol -> diagnosticsCollector.reportError(
                BuilderDiagnosticKind.IllegalProtocolCall(functionName, protocol, calleeName, calleeProtocol), callSiteSpan,
            )
            calleeProtocol == protocol -> if (coercion != null) diagnosticsCollector.reportError(
                BuilderDiagnosticKind.UnnecessaryCoercion(functionName, calleeName, protocol), coercion.span,
            )
            coercion == null -> diagnosticsCollector.reportError(
                BuilderDiagnosticKind.MissingCoercion(functionName, protocol, calleeName, calleeProtocol), callSiteSpan,
            )
            coercion.protocol != protocol -> diagnosticsCollector.reportError(
                BuilderDiagnosticKind.CoercionProtocolMismatch(functionName, protocol, coercion.protocol), coercion.span,
            )
            // A @pure island must be register-free; checked once its callee is built (see `islands`).
            calleeProtocol == Protocol.PURE -> {
                islands += invocation to callSiteSpan
                return true
            }
            // A @valid island inside @ready_valid (coercion C).
            else -> return true
        }
        return false
    }

    // Inside a @valid body no call may take more than one input channel - that would be a join.
    private fun validateNoJoin(invocation: Module.Invocation, inputCount: Int, callSiteSpan: SourceSpan) {
        if (protocol == Protocol.VALID && inputCount > 1) {
            diagnosticsCollector.reportError(
                BuilderDiagnosticKind.JoinInValidFunction(functionName, invocation.gaplFunctionName, inputCount),
                callSiteSpan,
            )
        }
    }

    // fork_eager(T, n) / fork_lazy(T, n): n copies of the input. Only the wiring for now - the handshake
    // that distinguishes eager from lazy arrives with @ready_valid's lowering (stage 2c).
    private fun lowerFork(invocation: Module.Invocation, callSiteSpan: SourceSpan): LoweredCall {
        val structure = invocation.interfaces[0]
        val branchCount = (invocation.parameters[0] as IntegerParameterValue).value.intValueExact()
        val source = conversionPassThrough(structure, callSiteSpan)
        val sourceWires = source.outputWireVectorGroups.single().wires()

        val branches = List(branchCount) { conversionPassThrough(structure, callSiteSpan) }
        branches.forEach { branch ->
            branch.inputWireVectorGroups.single().wires().zip(sourceWires).forEach { (branchWire, sourceWire) ->
                module.connect(branchWire, sourceWire)
            }
        }

        return LoweredCall(source.inputWireVectorGroups, branches.flatMap { it.outputWireVectorGroups })
    }

    // An anonymous PassThroughNode of one structure - the building block lowered calls are made of.
    private fun conversionPassThrough(structure: InterfaceStructure, callSiteSpan: SourceSpan) = createPassThroughNode(
        identifier = AnonymousIdentifierGenerator.genIdentifier(),
        inputWireVectorGroupsBuilder = { node -> listOf(buildInputWireVectorGroups(structure, node)) },
        outputWireVectorGroupsBuilder = { node -> listOf(buildOutputWireVectorGroups(structure, node)) },
    ).also { nodeSpans[it] = callSiteSpan }

    // project(t, v): view bit i is driven by base bit baseBits[i]. See ViewLayout for the numbering.
    private fun lowerProject(invocation: Module.Invocation, callSiteSpan: SourceSpan): LoweredCall {
        val baseBits = ViewLayout.decode(invocation.parameters)
        val base = conversionPassThrough(invocation.interfaces[0], callSiteSpan)
        val view = conversionPassThrough(invocation.interfaces[1], callSiteSpan)

        val baseWires = base.outputWireVectorGroups.single().wires()
        view.inputWireVectorGroups.single().wires().forEachIndexed { index, viewWire ->
            module.connect(viewWire, baseWires[baseBits[index]])
        }

        return LoweredCall(base.inputWireVectorGroups, view.outputWireVectorGroups)
    }

    // overlay(t, v): each base bit the view names comes from the view, every other bit from the base.
    private fun lowerOverlay(invocation: Module.Invocation, callSiteSpan: SourceSpan): LoweredCall {
        val viewBitForBaseBit = ViewLayout.decode(invocation.parameters)
            .withIndex()
            .associate { (viewBit, baseBit) -> baseBit to viewBit }
        val base = conversionPassThrough(invocation.interfaces[0], callSiteSpan)
        val view = conversionPassThrough(invocation.interfaces[1], callSiteSpan)
        val result = conversionPassThrough(invocation.interfaces[0], callSiteSpan)

        val baseWires = base.outputWireVectorGroups.single().wires()
        val viewWires = view.outputWireVectorGroups.single().wires()
        result.inputWireVectorGroups.single().wires().forEachIndexed { index, resultWire ->
            val viewBit = viewBitForBaseBit[index]
            module.connect(resultWire, if (viewBit != null) viewWires[viewBit] else baseWires[index])
        }

        return LoweredCall(base.inputWireVectorGroups + view.inputWireVectorGroups, result.outputWireVectorGroups)
    }

    // zeros(T): a T driven entirely by one all-zero literal.
    private fun lowerZeros(invocation: Module.Invocation, callSiteSpan: SourceSpan): LoweredCall {
        val result = conversionPassThrough(invocation.interfaces[0], callSiteSpan)
        val resultWires = result.inputWireVectorGroups.single().wires()

        val literalFunction = LiteralFunction(resultWires.size, BigInteger.ZERO)
        val literal = createPredefinedFunctionNode(
            identifier = AnonymousIdentifierGenerator.genIdentifier(),
            inputWireVectorGroupsBuilder = { node -> literalFunction.inputs.map { it.toInputWireVectorGroup(node) } },
            outputWireVectorGroupsBuilder = { node -> literalFunction.outputs.map { it.toOutputWireVectorGroup(node) } },
            predefinedFunction = literalFunction,
        ).also { nodeSpans[it] = callSiteSpan }

        literal.outputWireVectorGroups.single().wires().zip(resultWires).forEach { (literalWire, resultWire) ->
            module.connect(resultWire, literalWire)
        }

        return LoweredCall(emptyList(), result.outputWireVectorGroups)
    }

    private fun createNodeFromFunctionInvocation(
        invocationName: String,
        invocation: Module.Invocation,
        callSiteSpan: SourceSpan,
    ): BodyNode {
        // Step 1: Search predefined functions. If there is no predefined function with this name, search defined functions
        val matchingPredefinedFunction = PredefinedFunction.search(invocation)

        // Step 2: Create the node
        return (if (matchingPredefinedFunction != null) {
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
            val instantiation = moduleInstantiationTracker.visitModule(invocation, callSiteSpan)

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
        }).also { nodeSpans[it] = callSiteSpan }
    }

}