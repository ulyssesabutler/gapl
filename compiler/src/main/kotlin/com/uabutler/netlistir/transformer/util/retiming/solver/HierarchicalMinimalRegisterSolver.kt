package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.HierarchicalRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

data class TimingProperties(
    val inputDelay: Int?,
    val outputDelay: Int?,
    val combinationalDelay: Int?,
    val registerDelay: Int,
    val clockPeriod: Int,
    val registerCount: Int,
)

class HierarchicalMinimalRegisterSolver<G, N, E>(
    private val graphs: Collection<HierarchicalLeisersonCircuitGraph<G, N, E>>,
    private val expansionNodeFactory: () -> N,
    private val expansionEdgeValueFactory: () -> E,
    // See MinimalRegisterSolver's own parameter of this name - it is what makes the objective count
    // flip-flops rather than edges, and it is passed straight through to the per-module solve.
    private val edgeSourceBits: (WeightedGraph.Edge<N, E>) -> Collection<Any> = { listOf(Any()) },
) : HierarchicalSolver<G, N, E>(HierarchicalRetimingProblem(graphs)) {

    private data class SolveResult<G, N, E>(
        val retimedGraph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        // Null when the unretimed model couldn't be built - these are reporting-only stats, so a
        // failure to compute them must never fail the retiming itself. See computeUnretimedProperties.
        val unretimedProperties: TimingProperties?,
        val retimedProperties: TimingProperties,
        // r(super-out) - r(super-in), read straight off this graph's own ILP solution. This is the
        // "component retiming difference" the parent pins its contracted graph against, and it is
        // deliberately NOT re-derived from the change in registerDelay: paths to different sinks are
        // shifted by different amounts by a retiming, so a minimum taken over a set that contains
        // anything other than the real super-sink does not shift by r(s_o) - r(s_i). Any node inside
        // the module whose value is never consumed is such a sink, and getting this constant wrong
        // silently mis-pipelines every caller. See brainstorming/todo.md > ## Retiming.
        val retimingDifference: Int,
    )

    private data class ChildExpansion<N>(
        val inputNode: WeightedGraph.Node<N>,
        val outputNode: WeightedGraph.Node<N>,
        val inputDelayNode: WeightedGraph.Node<N>?,
        val outputDelayNode: WeightedGraph.Node<N>?,
        val combinationalDelayNode: WeightedGraph.Node<N>?,
        val retimingDifference: Int,
    )

    /**
     * How one already-solved child is summarised inside its parent's flat graph: the delays that
     * become the contracted graph's node weights, plus the starting weights of the two contracted
     * paths (c_i -> d_c -> c_o and c_i -> d_i -> d_o -> c_o).
     */
    private data class ChildSummary(
        val properties: TimingProperties,
        // w(c_i, d_c)
        val combinationalPathWeight: Int,
        // w(d_i, d_o)
        val delayPathWeight: Int,
        // r(c_o) - r(c_i), pinned by an equality constraint when this model is the one being solved
        val retimingDifference: Int,
    )

    /**
     * One module flattened to a solvable [LeisersonCircuitGraph]: its own leaf/virtual nodes plus a
     * contracted subgraph per child, and the flat nodes standing in for the module's declared
     * super-input/super-output.
     *
     * [boundaryInput]/[boundaryOutput] come from the hierarchical graph's explicit
     * `rootAttachment`/`leafAttachment`, never from `rootNodes()`/`leafNodes()` - a module can have
     * more than one graph-theoretic root or leaf (an internal value that is legitimately never
     * consumed is a leaf), which is exactly why those attachments are carried explicitly on
     * HierarchicalLeisersonCircuitGraph in the first place.
     */
    private inner class FlatModel(
        val graph: LeisersonCircuitGraph<G, N, E>,
        val expansions: Map<HierarchicalLeisersonCircuitGraph.Node<N>, ChildExpansion<N>>,
        val boundaryInput: WeightedGraph.Node<N>,
        val boundaryOutput: WeightedGraph.Node<N>,
        private val directFlatNode: Map<HierarchicalLeisersonCircuitGraph.Node<N>, WeightedGraph.Node<N>>,
    ) {
        /**
         * The flat node a hierarchical edge endpoint attaches to. A child collapses to two nodes, so
         * which one depends on the direction: edges *out of* a child leave its contracted output
         * node, edges *into* it arrive at its contracted input node.
         */
        fun flatNodeFor(node: HierarchicalLeisersonCircuitGraph.Node<N>, isSource: Boolean): WeightedGraph.Node<N> =
            directFlatNode[node] ?: expansions.getValue(node).let { if (isSource) it.outputNode else it.inputNode }
    }

    // Timing properties from the most recent solveOrNull call, keyed by (unretimed) root graph.
    // Exposed via timingPropertiesFromLastSolve so callers needing correct before/after stats
    // (e.g. HierarchicalRetimer's logging) don't have to recompute them by naively flattening a
    // retimed graph - see timingPropertiesFromLastSolve's doc comment for why that's unsafe.
    private var lastSolveResults: Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>> = emptyMap()

    override fun solveOrNull(targetClockPeriod: Int?): HierarchicalRetimingProblem<G, N, E>? {
        // Unconstrained ("no target period") hierarchical solving isn't implemented - matches
        // today's real limitation (HierarchicalRetimer previously required a non-null target).
        if (targetClockPeriod == null) return null

        val results = solveAll(targetClockPeriod)
        lastSolveResults = results
        // All-or-nothing: solveAll can silently omit a root whose solveSingle failed. The
        // Solver.solveOrNull contract is all-or-nothing, so a partial batch is a failed solve.
        if (!problem.roots.all { it in results }) return null

        return HierarchicalRetimingProblem(problem.roots.map { results.getValue(it).retimedGraph })
    }

    /**
     * (unretimedProperties, retimedProperties) for [root] from the most recent successful
     * solveOrNull call, or null if [root] wasn't part of it. The unretimed half is itself null when
     * those stats couldn't be computed - they are reporting-only and never block a retiming.
     *
     * These are the properties computed internally during the actual solve (via the per-level
     * "expansion" boundary bookkeeping) - deliberately NOT re-derived by the caller via
     * `root.flatten()`/`retimedGraph.flatten()`. Naively flattening an *unretimed* graph is fine
     * (that's how HierarchicalRetimingProblem's own computeClockPeriod/computePossibleClockPeriods
     * work), but naively flattening a *retimed* one is not: the boundary edge weights this solver
     * assigns are calibrated against the "expansion" node's synthetic topology (which folds in the
     * child's already-computed retiming difference), not the child's real internal structure, so
     * splicing the real structure back in can produce a graph that looks like it has an illegal
     * zero-register cycle even though the retiming itself is correct.
     */
    fun timingPropertiesFromLastSolve(root: HierarchicalLeisersonCircuitGraph<G, N, E>): Pair<TimingProperties?, TimingProperties>? =
        lastSolveResults[root]?.let { it.unretimedProperties to it.retimedProperties }

    private fun solveAll(targetClockPeriod: Int): Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>> {
        val results = mutableMapOf<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>>()
        val processed = mutableSetOf<HierarchicalLeisersonCircuitGraph<G, N, E>>()

        fun processGraph(graph: HierarchicalLeisersonCircuitGraph<G, N, E>) {
            if (graph in processed) return
            graph.childGraphs().forEach { processGraph(it) }
            solveSingle(graph, results, targetClockPeriod)?.let { results[graph] = it }
            processed.add(graph)
        }

        graphs.forEach { processGraph(it) }
        return results
    }

    /**
     * Reports every child instance that sits on a feedback loop in [graph].
     *
     * These are the instances where contraction hurts most: the loop's register count is invariant
     * under retiming, so once the child's own retiming difference has consumed those registers the
     * loop's external edges are pinned to zero, and the parent has to fit the child's whole
     * `outputDelay + inputDelay` into one clock period - two maxima taken over *all* ports, charging
     * the loop for paths that need not be on it. A module that is infeasible at every clock period
     * almost certainly has one of these. See brainstorming/todo.md > ## Retiming.
     */
    private fun logChildrenOnCycles(graph: HierarchicalLeisersonCircuitGraph<G, N, E>) {
        Logger.debug {
            val selfLooped = graph.edges.filter { it.source === it.sink }.map { it.source }
            val cyclicGroups = graph.stronglyConnectedComponentsTarjan().filter { it.size > 1 } +
                selfLooped.map { setOf(it) }

            val childrenOnCycles = cyclicGroups.map { component ->
                component to component.filterIsInstance<HierarchicalLeisersonCircuitGraph.ChildGraphNode<G, N, E>>()
            }.filter { (_, children) -> children.isNotEmpty() }

            if (childrenOnCycles.isEmpty()) {
                "No child instance lies on a feedback loop in graph=${graph.value}" +
                    " (${cyclicGroups.size} cyclic component(s), all of leaf nodes only)"
            } else {
                childrenOnCycles.joinToString(prefix = "Child instances on feedback loops in graph=${graph.value}: ") { (component, children) ->
                    "[loop of ${component.size} node(s): ${children.joinToString { it.childGraph.value.toString() }}]"
                }
            }
        }
    }

    /**
     * Flattens [graph] one level: its own nodes, plus a contracted subgraph per child built from
     * whatever [childSummary] says that child looks like from the outside.
     */
    private fun buildFlatModel(
        graph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        childSummary: (HierarchicalLeisersonCircuitGraph.ChildGraphNode<G, N, E>) -> ChildSummary,
    ): FlatModel {
        // Map from hierarchical node to its flat counterpart (for non-ChildGraphNode nodes)
        val directFlatNode = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, WeightedGraph.Node<N>>()
        // Map from hierarchical child node to its expansion (keyed by Node<N> to avoid casts when looking up edge endpoints)
        val expansions = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, ChildExpansion<N>>()
        val allFlatNodes = mutableListOf<WeightedGraph.Node<N>>()
        val expansionEdges = mutableListOf<WeightedGraph.Edge<N, E>>()

        // Leaf and virtual nodes map directly to a single WeightedGraph.Node
        graph.nodes.forEach { hierarchicalNode ->
            val nodeValue: N = hierarchicalNode.value
            when (hierarchicalNode) {
                is HierarchicalLeisersonCircuitGraph.LeafNode<*> -> {
                    val flatNode = WeightedGraph.Node(hierarchicalNode.weight, nodeValue)
                    allFlatNodes.add(flatNode)
                    directFlatNode[hierarchicalNode] = flatNode
                }
                is HierarchicalLeisersonCircuitGraph.VirtualNode<*> -> {
                    val flatNode = WeightedGraph.Node(0, nodeValue)
                    allFlatNodes.add(flatNode)
                    directFlatNode[hierarchicalNode] = flatNode
                }
                is HierarchicalLeisersonCircuitGraph.ChildGraphNode<*, *, *> -> { /* handled below */ }
            }
        }

        // Child nodes expand into contracted subgraphs using the summary supplied by the caller
        graph.childNodes().forEach { childNode ->
            val summary = childSummary(childNode)
            val properties = summary.properties

            val expansionInputNode = WeightedGraph.Node<N>(0, expansionNodeFactory())
            val expansionOutputNode = WeightedGraph.Node<N>(0, expansionNodeFactory())
            allFlatNodes.add(expansionInputNode)
            allFlatNodes.add(expansionOutputNode)

            var inputDelayNode: WeightedGraph.Node<N>? = null
            var outputDelayNode: WeightedGraph.Node<N>? = null
            if (properties.inputDelay != null && properties.outputDelay != null) {
                inputDelayNode = WeightedGraph.Node(properties.inputDelay, expansionNodeFactory())
                outputDelayNode = WeightedGraph.Node(properties.outputDelay, expansionNodeFactory())
                allFlatNodes.add(inputDelayNode)
                allFlatNodes.add(outputDelayNode)

                expansionEdges.add(WeightedGraph.Edge(0, expansionInputNode, inputDelayNode, expansionEdgeValueFactory()))
                expansionEdges.add(WeightedGraph.Edge(0, outputDelayNode, expansionOutputNode, expansionEdgeValueFactory()))
                expansionEdges.add(WeightedGraph.Edge(summary.delayPathWeight, inputDelayNode, outputDelayNode, expansionEdgeValueFactory()))
            }

            var combinationalDelayNode: WeightedGraph.Node<N>? = null
            if (properties.combinationalDelay != null) {
                combinationalDelayNode = WeightedGraph.Node(properties.combinationalDelay, expansionNodeFactory())
                allFlatNodes.add(combinationalDelayNode)

                expansionEdges.add(WeightedGraph.Edge(summary.combinationalPathWeight, expansionInputNode, combinationalDelayNode, expansionEdgeValueFactory()))
                expansionEdges.add(WeightedGraph.Edge(0, combinationalDelayNode, expansionOutputNode, expansionEdgeValueFactory()))
            }

            expansions[childNode] = ChildExpansion(
                inputNode = expansionInputNode,
                outputNode = expansionOutputNode,
                inputDelayNode = inputDelayNode,
                outputDelayNode = outputDelayNode,
                combinationalDelayNode = combinationalDelayNode,
                retimingDifference = summary.retimingDifference,
            )
        }

        fun flatNodeFor(node: HierarchicalLeisersonCircuitGraph.Node<N>, isSource: Boolean): WeightedGraph.Node<N> =
            directFlatNode[node] ?: expansions.getValue(node).let { if (isSource) it.outputNode else it.inputNode }

        // Expansion edges first, then the graph's own edges
        val allFlatEdges = expansionEdges + graph.edges.map { hierarchicalEdge ->
            WeightedGraph.Edge(
                weight = hierarchicalEdge.weight,
                source = flatNodeFor(hierarchicalEdge.source, isSource = true),
                sink = flatNodeFor(hierarchicalEdge.sink, isSource = false),
                value = hierarchicalEdge.value,
            )
        }

        return FlatModel(
            graph = LeisersonCircuitGraph(graph.value, allFlatNodes, allFlatEdges),
            expansions = expansions,
            // Matches how HierarchicalLeisersonCircuitGraph.flattenToWeightedGraph resolves the two
            // attachments: the root attachment is where a caller's edges arrive, the leaf attachment
            // is where they leave from.
            boundaryInput = flatNodeFor(graph.rootAttachment, isSource = false),
            boundaryOutput = flatNodeFor(graph.leafAttachment, isSource = true),
            directFlatNode = directFlatNode,
        )
    }

    private fun solveSingle(
        graph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        childResults: Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>>,
        targetClockPeriod: Int,
    ): SolveResult<G, N, E>? = Logger.run("Retiming hierarchical graph") {
        if (graph.childNodes().any { it.childGraph !in childResults }) {
            Logger.error { "Missing child solve result — child was not processed first (graph=${graph.value}, missing=${graph.childNodes().filter { it.childGraph !in childResults }.map { it.childGraph.value }})" }
            return@run null
        }

        logChildrenOnCycles(graph)

        // Step 1: Flatten to LeisersonCircuitGraph, each child contracted to its retimed summary.
        // The contracted paths start at -delta / -delta + 1 so that, once the equality constraints
        // below have been applied, the retimed weights come out at 0 (combinational path) and 1
        // (input-delay path, which must stay register-separated).
        val model = buildFlatModel(graph) { childNode ->
            val childResult = childResults.getValue(childNode.childGraph)
            val retimingDifference = childResult.retimingDifference
            ChildSummary(
                properties = childResult.retimedProperties,
                combinationalPathWeight = -retimingDifference,
                delayPathWeight = -retimingDifference + 1,
                retimingDifference = retimingDifference,
            )
        }

        // Step 2: Equality constraints pinning each contracted subgraph
        val equalityConstraints = mutableListOf<NodeEqualityConstraint<N>>()
        model.expansions.values.forEach { expansion ->
            equalityConstraints.add(NodeEqualityConstraint(expansion.inputNode, expansion.outputNode, expansion.retimingDifference.toLong()))
            if (expansion.inputDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.inputNode, expansion.inputDelayNode, 0L))
            }
            if (expansion.outputDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.outputNode, expansion.outputDelayNode, 0L))
            }
            if (expansion.combinationalDelayNode != null) {
                equalityConstraints.add(NodeEqualityConstraint(expansion.outputNode, expansion.combinationalDelayNode, 0L))
            }
        }

        // Step 3: Run the flat solver
        val minimalRegisterSolver = MinimalRegisterSolver(MonolithicRetimingProblem(model.graph), equalityConstraints, edgeSourceBits)
        val minimalResult = minimalRegisterSolver.solveOrNull(targetClockPeriod)
        if (minimalResult == null) {
            // Debug, not error: this fires routinely for every infeasible probe during
            // findMinimumClockPeriod's binary search, not just for a genuine bug.
            Logger.debug { "MinimalRegisterSolver infeasible for graph=${graph.value} at period=$targetClockPeriod (nodes=${model.graph.nodes.size}, edges=${model.graph.edges.size})" }
            return@run null
        }
        val nodeLags = minimalRegisterSolver.lastSolveNodeLags
            ?: throw IllegalStateException("MinimalRegisterSolver returned a solution without recording its retiming labels")

        // Step 4: This graph's own component retiming difference, straight off the ILP solution
        val retimingDifference = nodeLags.getValue(model.boundaryOutput) - nodeLags.getValue(model.boundaryInput)

        // Worth logging on its own line: this single integer is the entire interface between this
        // module's solve and every parent's, and a parent that cannot absorb it is infeasible at
        // *any* clock period (a caller's feedback loop through this module would have to change its
        // register count, which retiming cannot do).
        Logger.debug { "Component retiming difference for graph=${graph.value}: $retimingDifference" }

        // Step 5: Rebuild the hierarchical graph with retimed edge weights.
        // Point each ChildGraphNode at its own (already-solved) retimed graph, so the graph returned here is
        // self-consistent and safe to flatten(). ChildGraphNode is a data class keyed in part by childGraph, so
        // both the node list and every edge endpoint that touches a child must be swapped together — leaving edges
        // pointing at the old node would desync nodes/edges (breaking rootNodes()/leafNodes() and flatten()).
        val retimedChildNodeByOriginal = mutableMapOf<HierarchicalLeisersonCircuitGraph.Node<N>, HierarchicalLeisersonCircuitGraph.ChildGraphNode<G, N, E>>()
        graph.childNodes().forEach { childNode ->
            retimedChildNodeByOriginal[childNode] = childNode.copy(
                childGraph = childResults.getValue(childNode.childGraph).retimedGraph,
            )
        }

        fun retimedNodeFor(node: HierarchicalLeisersonCircuitGraph.Node<N>) = retimedChildNodeByOriginal[node] ?: node

        // Retimed weights are recomputed from the labels (w + r(sink) - r(source)) rather than read
        // back out of the solver's edge list by position - matching flat edges to hierarchical ones
        // by index silently produces a scrambled retiming if anything ever reorders or dedups edges.
        val retimedHierarchicalEdges = graph.edges.map { hierarchicalEdge ->
            val flatSource = model.flatNodeFor(hierarchicalEdge.source, isSource = true)
            val flatSink = model.flatNodeFor(hierarchicalEdge.sink, isSource = false)
            hierarchicalEdge.copy(
                weight = hierarchicalEdge.weight + nodeLags.getValue(flatSink) - nodeLags.getValue(flatSource),
                source = retimedNodeFor(hierarchicalEdge.source),
                sink = retimedNodeFor(hierarchicalEdge.sink),
            )
        }

        val retimedGraph = HierarchicalLeisersonCircuitGraph(
            value = graph.value,
            nodes = graph.nodes.map { retimedNodeFor(it) },
            edges = retimedHierarchicalEdges,
            rootAttachment = retimedNodeFor(graph.rootAttachment),
            leafAttachment = retimedNodeFor(graph.leafAttachment),
        )

        // Step 6: Stats. registerCount is overridden with this module's *own* registers - the flat
        // graph's edge sum would also count the synthetic register on each contracted input-delay
        // path and would miss every real register inside the children.
        val retimedProperties = computeTimingProperties(minimalResult.graph, model.boundaryInput, model.boundaryOutput)
            .copy(registerCount = retimedHierarchicalEdges.sumOf { it.weight })

        // The four numbers below are the *entire* interface between this module's solve and every
        // parent's. Logged together because a parent that cannot absorb them is infeasible at any
        // clock period, and the reason is only visible from the summary: a caller whose feedback
        // loop runs through this module has to fit inputDelay + outputDelay into one clock period
        // whenever the loop's registers all end up inside the module, since these are maxima over
        // *all* ports and so charge the loop for paths that aren't on it.
        Logger.debug {
            "Boundary summary for graph=${graph.value}: " +
                "delta=$retimingDifference inputDelay=${retimedProperties.inputDelay} " +
                "outputDelay=${retimedProperties.outputDelay} combinationalDelay=${retimedProperties.combinationalDelay}"
        }

        SolveResult(
            retimedGraph = retimedGraph,
            unretimedProperties = computeUnretimedProperties(graph, childResults),
            retimedProperties = retimedProperties,
            retimingDifference = retimingDifference,
        )
    }

    /**
     * Reporting-only "before" stats for [graph]: the same one-level flattening as the solve, but with
     * every child contracted to its *unretimed* summary and no retiming applied.
     *
     * Measuring these on the graph the solve runs against would be meaningless, because that graph
     * already has the children retimed and its contracted paths carry negative starting weights
     * (chosen so the weights come out right only *after* retiming) - a negative weight reads as
     * "registered" to computeClockPeriod and as a negative register count to any edge sum.
     *
     * Returns null rather than throwing on any failure. These numbers only feed logging, and the
     * unretimed model can legitimately fail to build where the retimed one succeeded: a child that
     * is combinational before retiming but registered after gives the unretimed contracted graph a
     * zero-weight c_i -> c_o path, which turns external combinational feedback around that child
     * into a (false) zero-weight cycle that LeisersonCircuitGraph's constructor rejects.
     */
    private fun computeUnretimedProperties(
        graph: HierarchicalLeisersonCircuitGraph<G, N, E>,
        childResults: Map<HierarchicalLeisersonCircuitGraph<G, N, E>, SolveResult<G, N, E>>,
    ): TimingProperties? {
        if (graph.childNodes().any { childResults.getValue(it.childGraph).unretimedProperties == null }) {
            Logger.debug { "Skipping unretimed stats for graph=${graph.value}: a child has no unretimed stats" }
            return null
        }

        return try {
            val model = buildFlatModel(graph) { childNode ->
                val properties = childResults.getValue(childNode.childGraph).unretimedProperties!!
                ChildSummary(
                    properties = properties,
                    // No retiming is applied to this model, so the contracted weights are the
                    // child's real boundary numbers: its combinational path (when it has one) is
                    // combinational, and its input-delay path carries the child's own input-to-output
                    // register count, floored at 1 so d_i and d_o stay register-separated.
                    combinationalPathWeight = 0,
                    delayPathWeight = maxOf(1, properties.registerDelay),
                    retimingDifference = 0,
                )
            }

            computeTimingProperties(model.graph, model.boundaryInput, model.boundaryOutput)
                .copy(registerCount = graph.edges.sumOf { it.weight })
        } catch (e: Exception) {
            Logger.debug { "Could not compute unretimed stats for graph=${graph.value}: $e" }
            null
        }
    }
}

/**
 * Timing summary of one already-flattened module, measured between its declared boundary nodes.
 *
 * [boundaryInput]/[boundaryOutput] must be the flat counterparts of the hierarchical graph's
 * `rootAttachment`/`leafAttachment`. They are parameters rather than being rediscovered via
 * `rootNodes()`/`leafNodes()` because degree does not identify them: any internal node whose value
 * is never consumed is also a graph-theoretic leaf, and folding such a node into the "outputs" set
 * corrupts [TimingProperties.registerDelay] (a minimum over a set of sinks that a retiming shifts by
 * *different* amounts) and misattributes combinational paths between [TimingProperties.inputDelay]
 * and [TimingProperties.combinationalDelay].
 */
fun <G, N, E> computeTimingProperties(
    graph: LeisersonCircuitGraph<G, N, E>,
    boundaryInput: WeightedGraph.Node<N>,
    boundaryOutput: WeightedGraph.Node<N>,
): TimingProperties {
    val connectionsFromInput = graph.findFastestConnectionsFromNode(boundaryInput)
    val fullPath = connectionsFromInput.firstOrNull { it.sink === boundaryOutput }

    val registerDelay = fullPath?.registerCount ?: 0
    val combinationalDelay = fullPath?.takeIf { it.registerCount == 0 }?.delay

    // The longest combinational path starting at an input and ending anywhere other than an output.
    // Taking the max over every combinationally-reachable node (rather than only over the ends of
    // maximal paths) is equivalent, since a prefix is never longer than the path it prefixes.
    val inputDelay = connectionsFromInput
        .filter { it.sink !== boundaryOutput }
        .filter { it.registerCount == 0 }
        .maxOfOrNull { it.delay }

    val reversedGraph = LeisersonCircuitGraph(
        value = graph.value,
        nodes = graph.nodes,
        edges = graph.edges.map { WeightedGraph.Edge(it.weight, it.sink, it.source, it.value) },
    )

    val outputDelay = reversedGraph.findFastestConnectionsFromNode(boundaryOutput)
        .filter { it.sink !== boundaryInput }
        .filter { it.registerCount == 0 }
        .maxOfOrNull { it.delay }

    return TimingProperties(
        inputDelay = inputDelay,
        outputDelay = outputDelay,
        combinationalDelay = combinationalDelay,
        registerDelay = registerDelay,
        clockPeriod = graph.computeClockPeriod(),
        registerCount = graph.edges.sumOf { it.weight },
    )
}
