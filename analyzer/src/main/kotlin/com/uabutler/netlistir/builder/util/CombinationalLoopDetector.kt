package com.uabutler.netlistir.builder.util

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWireVectorGroup
import com.uabutler.netlistir.netlist.WireVectorGroup
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.netlistir.util.graph.NetlistLeisersonCircuitConverter
import com.uabutler.util.graph.UnweightedGraph

/**
 * Detects purely-combinational feedback loops across the whole program - a node (possibly reached
 * through one or more function calls) whose output eventually drives its own input with no register
 * anywhere along the path.
 *
 * Computes, bottom-up over the invocation graph (leaves/callees first), a small "port-reachability
 * summary" per module: for each pair of the module's own named boundary ports (a whole
 * [WireVectorGroup] - "i", "o", etc. - not individual bits), does a zero-weight (register-free) path
 * exist between them? A caller treats a call to an already-processed module as a transparent box
 * wired directly via that summary - a handful of synthetic edges - rather than inlining the callee's
 * own internals. This is what lets total work stay bounded by the sum of each *distinct* module's own
 * local size, regardless of how many places call it or how deep the nesting goes - an earlier
 * implementation instead recursively flattened the whole call tree (reusing the same hierarchical
 * graph machinery [com.uabutler.netlistir.transformer.Retimer] builds on), which re-flattened a
 * shared child graph once per *call site* rather than once per distinct module: work scaled with the
 * number of root-to-leaf paths through the invocation DAG, exponential for designs with call reuse
 * nested several levels deep (e.g. netfpga's aes processor - 696s for this check alone, ~14000x
 * slower than this version, on an otherwise-identical compile with byte-identical Verilog output).
 *
 * Works at named-port granularity, not individual-bit granularity: tracking reachability per bit
 * turns every wide bus (128+ bits is common in these designs) into O(width^2) edges per node and per
 * call - actually *regressing* against the flattening approach on a design without deep call reuse
 * (cms went from ~19s to ~154s under a bit-level version of this same algorithm, before falling to
 * ~0.1s once collapsed to port granularity). Named-port granularity assumes every bit of one port has
 * the same reachability to every bit of another - true for how GAPL functions are actually written (a
 * port is either combinationally connected to another or isn't, not mixed at the bit level) - the
 * same coarseness the old per-node approach already assumed for ordinary nodes, just extended to call
 * boundaries too, not a new over-approximation.
 *
 * Summaries also carry only a boolean per port pair, not a witness path - materializing and
 * concatenating witness node-lists eagerly while computing every summary reintroduces a milder form
 * of the same blowup this approach exists to avoid (a witness embedded at one nesting level gets
 * re-embedded at every level above it, compounding with depth - this OOM'd on cms during
 * development). Instead, [reconstructWitness] walks a specific module's local edges on demand,
 * recursing into a callee only along the *one* path an actual reported cycle uses - paid only for
 * genuine loops (rare to nonexistent in a clean design), never for the summary computation every
 * module pays regardless of whether anything is ever reported.
 *
 * Cycle detection happens locally, per module, on that module's own (small) local graph - every
 * genuine loop is found and reported exactly once, at the smallest module in which it actually
 * closes. No "root modules only" restriction is needed (unlike the flattening approach, which needed
 * one specifically to avoid re-reporting the same loop once per level of call nesting - not a concern
 * here since nothing is inlined more than once).
 *
 * Returns one entry per independent loop found - the [Node]s involved, ranked most-to-least relevant
 * for a human reader: a name the user actually chose beats one synthesized by
 * [com.uabutler.util.AnonymousIdentifierGenerator] (e.g. an inlined `+`/`xor` gate, or expanding a
 * generic stdlib helper); among named nodes, a declared node ([BodyNode]) beats a function's own
 * parameter ([com.uabutler.netlistir.netlist.IONode], inherently generic-named since it's reused
 * identically across every call to that function - `i`/`o` and the like); ties break alphabetically
 * by node name, then by owning function name. That last tie-break matters for more than cosmetics:
 * it's what makes the ranking fully content-determined rather than dependent on iteration order,
 * which is what lets two structurally identical loops (e.g. the same buggy generic function called
 * once per value of an unrelated generic parameter, as in aes/test.gapl's round_key(1..10)) rank
 * their nodes identically, so the resulting diagnostics are equal and collapse via
 * [com.uabutler.diagnostics.DiagnosticsCollector]'s dedup, instead of surviving as near-duplicates
 * that only differ in node order. Empty if there are no loops.
 */
fun findCombinationalLoops(modules: Collection<MutableModule>): List<List<Node>> {
    val moduleByInvocation = modules.associateBy { it.invocation }
    val topoOrder = InvocationGraph(modules).topologicalSort().reversed() // leaves (callees) first

    val summaries = mutableMapOf<Module.Invocation, ModuleSummary>()
    val localEdgesByInvocation = mutableMapOf<Module.Invocation, List<EdgeSpec>>()
    val foundLoops = mutableListOf<List<Node>>()

    for (module in topoOrder) {
        val edges = buildLocalEdges(module, moduleByInvocation, summaries)
        localEdgesByInvocation[module.invocation] = edges

        findLocalCycles(edges).forEach { sccGroups ->
            foundLoops.add(expandSccToRealNodes(sccGroups, edges, localEdgesByInvocation))
        }

        summaries[module.invocation] = computeSummary(module, edges)
    }

    return foundLoops
        .map { nodes ->
            nodes.sortedWith(
                compareBy(
                    { node: Node -> node.name().startsWith("anonymous_") },
                    { node: Node -> node !is BodyNode },
                    { node: Node -> node.name() },
                    { node: Node -> node.parentModule.invocation.gaplFunctionName },
                )
            )
        }
        .sortedWith(
            compareBy(
                { loop: List<Node> -> loop.firstOrNull()?.name() ?: "" },
                { loop: List<Node> -> loop.firstOrNull()?.parentModule?.invocation?.gaplFunctionName ?: "" },
            )
        )
}

private sealed class EdgeInfo
private data class LocalHop(val node: Node) : EdgeInfo()
private data class CallBoundary(
    val calleeInvocation: Module.Invocation,
    val calleeInGroup: OutputWireVectorGroup,
    val calleeOutGroup: InputWireVectorGroup,
) : EdgeInfo()

private data class EdgeSpec(val source: WireVectorGroup<*>, val sink: WireVectorGroup<*>, val info: EdgeInfo)

// Module-input-port (an InputNode's own OutputWireVectorGroup) -> set of module-output-ports (an
// OutputNode's own InputWireVectorGroup) reachable from it via a zero-weight path.
private class ModuleSummary(val portReachability: Map<OutputWireVectorGroup, Set<InputWireVectorGroup>>)

private fun buildLocalEdges(
    module: MutableModule,
    moduleByInvocation: Map<Module.Invocation, MutableModule>,
    summaries: Map<Module.Invocation, ModuleSummary>,
): List<EdgeSpec> {
    // Group-level, deduped: many individual wire pairs collapse to the same (sourceGroup,
    // sinkGroup) fact - dedup here is what keeps this O(distinct port pairs), not O(bits).
    val interNodeEdges = NetlistLeisersonCircuitConverter.getNonRegisterConnections(module)
        .filter { it.weight == 0 }
        .map { conn ->
            val sourceGroup = conn.source.parentWireVector.parentGroup
            val sinkGroup = conn.sink.parentWireVector.parentGroup
            EdgeSpec(source = sourceGroup, sink = sinkGroup, info = LocalHop(sinkGroup.parentNode))
        }
        .distinct()

    // A Connection only ever links wires belonging to two *different* nodes - there's no explicit
    // Connection representing "this node's own output depends on this node's own input," even
    // though that's exactly what a combinational node (anything not a register) does. A single
    // self-looping node (e.g. `declare x: wire => x;`) is invisible to cycle detection without this
    // made explicit. Every input port of an ordinary combinational node is conservatively assumed to
    // reach every output port of that same node at zero weight - calls are handled separately below
    // via port-level summaries specifically because that blanket assumption would be wrong for them
    // (a callee's own register can disconnect some or all input/output port pairs).
    val intraNodeEdges = module.getNodes()
        .filterNot { NetlistLeisersonCircuitConverter.isRegisterNode(it) }
        .filterNot { it is ModuleInvocationNode }
        .flatMap { node ->
            node.inputWireVectorGroups.flatMap { input ->
                node.outputWireVectorGroups.map { output -> EdgeSpec(source = input, sink = output, info = LocalHop(node)) }
            }
        }

    val syntheticEdges = module.getBodyNodes().filterIsInstance<ModuleInvocationNode>().flatMap { callNode ->
        val summary = summaries.getValue(callNode.invocation)
        val calleeModule = moduleByInvocation.getValue(callNode.invocation)

        // Both sides were built from the same ordered InterfaceDescription list (see
        // ModuleInstantiationTracker.visitModule / NodeBuilder.createNodeFromFunctionInvocation) -
        // each InputNode/OutputNode contributes exactly one group ("only"), in declaration order,
        // matching this call site's own per-port groups one-for-one positionally - the same pattern
        // NodeCopier.createWirePairs already relies on for individual wires.
        val calleeToCallSiteInput = calleeModule.getInputNodes().flatMap { it.outputWireVectorGroups }
            .zip(callNode.inputWireVectorGroups).toMap()
        val calleeToCallSiteOutput = calleeModule.getOutputNodes().flatMap { it.inputWireVectorGroups }
            .zip(callNode.outputWireVectorGroups).toMap()

        summary.portReachability.flatMap { (calleeInGroup, reachable) ->
            val callSiteIn = calleeToCallSiteInput.getValue(calleeInGroup)
            reachable.map { calleeOutGroup ->
                val callSiteOut = calleeToCallSiteOutput.getValue(calleeOutGroup)
                EdgeSpec(
                    source = callSiteIn,
                    sink = callSiteOut,
                    info = CallBoundary(callNode.invocation, calleeInGroup, calleeOutGroup),
                )
            }
        }
    }

    return interNodeEdges + intraNodeEdges + syntheticEdges
}

private fun findLocalCycles(edges: List<EdgeSpec>): List<Set<WireVectorGroup<*>>> {
    if (edges.isEmpty()) return emptyList()

    val allGroups = (edges.map { it.source } + edges.map { it.sink }).distinct()
    val groupToGraphNode = allGroups.associateWith { UnweightedGraph.Node(it) }
    val graphEdges = edges.map {
        UnweightedGraph.Edge(
            source = groupToGraphNode.getValue(it.source),
            sink = groupToGraphNode.getValue(it.sink),
            value = it.info,
        )
    }
    val graph = UnweightedGraph(groupToGraphNode.values.toList(), graphEdges)

    return graph.stronglyConnectedComponentsTarjan()
        .filter { scc -> scc.size > 1 || graphEdges.any { it.source == it.sink && it.source in scc } }
        .map { scc -> scc.map { it.value }.toSet() }
}

private fun expandSccToRealNodes(
    sccGroups: Set<WireVectorGroup<*>>,
    edges: List<EdgeSpec>,
    localEdgesByInvocation: Map<Module.Invocation, List<EdgeSpec>>,
): List<Node> {
    // A ModuleInvocationNode's own ports are this model's synthetic call-boundary vertices, not
    // something a user would recognize. What actually happened inside the call is reconstructed on
    // demand below instead.
    val fromGroups = sccGroups.map { it.parentNode }.filterNot { it is ModuleInvocationNode }
    val fromWitnesses = edges
        .filter { it.source in sccGroups && it.sink in sccGroups }
        .flatMap { spec ->
            val info = spec.info as? CallBoundary ?: return@flatMap emptyList()
            reconstructWitness(info.calleeInvocation, info.calleeInGroup, info.calleeOutGroup, localEdgesByInvocation)
        }
    return (fromGroups + fromWitnesses).distinct()
}

// Re-walks one specific module's own local edges to recover an actual path of real Nodes from one
// boundary port to another - only ever called for the handful of edges genuinely on a reported
// cycle, so its cost is bounded by that one cycle's real size, not by every reachable port pair in
// the program.
private fun reconstructWitness(
    moduleInvocation: Module.Invocation,
    fromGroup: WireVectorGroup<*>,
    toGroup: WireVectorGroup<*>,
    localEdgesByInvocation: Map<Module.Invocation, List<EdgeSpec>>,
): List<Node> {
    val edges = localEdgesByInvocation.getValue(moduleInvocation)
    val adjacency = edges.groupBy { it.source }

    val cameFrom = mutableMapOf<WireVectorGroup<*>, EdgeSpec>()
    val visited = mutableSetOf(fromGroup)
    val queue = ArrayDeque<WireVectorGroup<*>>(listOf(fromGroup))

    while (queue.isNotEmpty() && toGroup !in visited) {
        val current = queue.removeFirst()
        for (edge in adjacency[current].orEmpty()) {
            if (visited.add(edge.sink)) {
                cameFrom[edge.sink] = edge
                queue.add(edge.sink)
            }
        }
    }

    val pathEdges = mutableListOf<EdgeSpec>()
    var walk = toGroup
    while (walk != fromGroup) {
        val edge = cameFrom.getValue(walk)
        pathEdges.add(edge)
        walk = edge.source
    }
    pathEdges.reverse()

    val boundaryNodes = listOf(fromGroup, toGroup).map { it.parentNode }
    val hopNodes = pathEdges.flatMap { spec ->
        when (val info = spec.info) {
            is LocalHop -> listOfNotNull(info.node.takeUnless { it is ModuleInvocationNode })
            is CallBoundary -> reconstructWitness(info.calleeInvocation, info.calleeInGroup, info.calleeOutGroup, localEdgesByInvocation)
        }
    }
    return (boundaryNodes + hopNodes).distinct()
}

private fun computeSummary(module: MutableModule, edges: List<EdgeSpec>): ModuleSummary {
    val adjacency = edges.groupBy { it.source }
    val moduleInputGroups = module.getInputNodes().flatMap { it.outputWireVectorGroups }
    val moduleOutputGroups = module.getOutputNodes().flatMap { it.inputWireVectorGroups }.toSet()

    val portReachability = mutableMapOf<OutputWireVectorGroup, Set<InputWireVectorGroup>>()

    for (startGroup in moduleInputGroups) {
        val reached = mutableSetOf<InputWireVectorGroup>()
        val visited = mutableSetOf<WireVectorGroup<*>>(startGroup)
        val queue = ArrayDeque<WireVectorGroup<*>>(listOf(startGroup))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current in moduleOutputGroups) reached.add(current as InputWireVectorGroup)

            for (edge in adjacency[current].orEmpty()) {
                if (visited.add(edge.sink)) queue.add(edge.sink)
            }
        }

        if (reached.isNotEmpty()) portReachability[startGroup] = reached
    }

    return ModuleSummary(portReachability)
}
