package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.PortHierarchicalRetimingProblem
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.PortHierarchicalCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import java.util.IdentityHashMap

/**
 * What one solved module tells its callers about its boundary.
 *
 * Every field is per-port or per-port-pair, which is the whole point: the module-wide equivalents
 * (`HierarchicalMinimalRegisterSolver`'s single `delta`/`inputDelay`/`outputDelay`) are maxima over
 * all ports, and charging a caller's feedback loop for paths that are not on it is what makes
 * designs like netfpga's CMS infeasible at every clock period. See
 * `brainstorming/per-port-hierarchical-retiming.md`.
 *
 * [portLags] are raw `r(p)` values, meaningful only up to a global constant - callers use their
 * differences. They replace the scalar retiming difference, and are what lets one port's path be
 * pipelined without dragging every other port's path along with it.
 */
data class PortBoundarySummary<N>(
    val portLags: Map<PortHierarchicalCircuitGraph.LeafNode<N>, Int>,
    val inputDelays: Map<PortHierarchicalCircuitGraph.LeafNode<N>, Int>,
    val outputDelays: Map<PortHierarchicalCircuitGraph.LeafNode<N>, Int>,
    val pairRegisters: Map<PortPair<N>, Int>,
    val pairCombinationalDelays: Map<PortPair<N>, Int>,
    /**
     * Which ports' lags are actually related to each other, as a component id per port.
     *
     * A lag *difference* only means something when some chain of the module's own edges connects the
     * two ports: that chain's register counts are fixed once the module is solved, so a caller has
     * to reproduce the difference. Ports in different weakly-connected components have no such
     * chain, so their relative lag is whatever the ILP happened to pick - genuinely arbitrary, and
     * unstable across runs. Propagating that arbitrary value to a caller as a hard constraint
     * over-constrains it for no reason, and can make an otherwise feasible parent infeasible.
     */
    val portComponents: Map<PortHierarchicalCircuitGraph.LeafNode<N>, Int>,
    val clockPeriod: Int,
    val ownRegisterCount: Int,
)

data class PortPair<N>(
    val input: PortHierarchicalCircuitGraph.LeafNode<N>,
    val output: PortHierarchicalCircuitGraph.LeafNode<N>,
)

class PerPortHierarchicalMinimalRegisterSolver<G, N, E>(
    private val graphs: Collection<PortHierarchicalCircuitGraph<G, N, E>>,
    private val expansionNodeFactory: () -> N,
    private val expansionEdgeValueFactory: () -> E,
    // See MinimalRegisterSolver's own parameter of this name - it is what makes the objective count
    // flip-flops rather than edges, and it is passed straight through to the per-module solve.
    private val edgeSourceBits: (WeightedGraph.Edge<N, E>) -> Collection<Any> = { listOf(Any()) },
) : PortHierarchicalSolver<G, N, E>(PortHierarchicalRetimingProblem(graphs)) {

    private data class SolveResult<G, N, E>(
        val retimedGraph: PortHierarchicalCircuitGraph<G, N, E>,
        val summary: PortBoundarySummary<N>,
    )

    private var lastSolveSummaries: Map<PortHierarchicalCircuitGraph<G, N, E>, PortBoundarySummary<N>> = emptyMap()

    /** Boundary summary for [graph] from the most recent successful solve, or null. */
    fun summaryFromLastSolve(graph: PortHierarchicalCircuitGraph<G, N, E>): PortBoundarySummary<N>? =
        lastSolveSummaries[graph]

    override fun solveOrNull(targetClockPeriod: Int?): PortHierarchicalRetimingProblem<G, N, E>? {
        if (targetClockPeriod == null) return null

        val results = IdentityHashMap<PortHierarchicalCircuitGraph<G, N, E>, SolveResult<G, N, E>>()
        val processed = IdentityHashMap<PortHierarchicalCircuitGraph<G, N, E>, Boolean>()
        val topLevel = problem.topLevelGraphs.toIdentitySet()

        fun processGraph(graph: PortHierarchicalCircuitGraph<G, N, E>) {
            if (processed[graph] == true) return
            graph.childInstances().forEach { processGraph(it.childGraph) }
            solveSingle(graph, results, targetClockPeriod, isTopLevel = graph in topLevel)?.let { results[graph] = it }
            processed[graph] = true
        }

        graphs.forEach { processGraph(it) }

        // All-or-nothing, matching Solver.solveOrNull's contract.
        if (problem.graphs.any { results[it] == null }) {
            lastSolveSummaries = emptyMap()
            return null
        }

        lastSolveSummaries = IdentityHashMap<PortHierarchicalCircuitGraph<G, N, E>, PortBoundarySummary<N>>().apply {
            results.forEach { (graph, result) -> put(graph, result.summary) }
        }

        return PortHierarchicalRetimingProblem(problem.graphs.map { results.getValue(it).retimedGraph })
    }

    /** One child instance's contracted subgraph inside its parent's flat graph. */
    private class ChildExpansion<N>(
        val boundaryNodes: Map<PortHierarchicalCircuitGraph.LeafNode<N>, WeightedGraph.Node<N>>,
        val equalityConstraints: List<NodeEqualityConstraint<N>>,
    )

    private fun solveSingle(
        graph: PortHierarchicalCircuitGraph<G, N, E>,
        childResults: Map<PortHierarchicalCircuitGraph<G, N, E>, SolveResult<G, N, E>>,
        targetClockPeriod: Int,
        isTopLevel: Boolean,
    ): SolveResult<G, N, E>? = Logger.run("Retiming per-port hierarchical graph") {
        val allFlatNodes = mutableListOf<WeightedGraph.Node<N>>()
        val allFlatEdges = mutableListOf<WeightedGraph.Edge<N, E>>()
        val equalityConstraints = mutableListOf<NodeEqualityConstraint<N>>()

        // Step 1: this module's own nodes
        val flatByLeaf = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, WeightedGraph.Node<N>>()
        graph.nodes.filterIsInstance<PortHierarchicalCircuitGraph.LeafNode<N>>().forEach { leaf ->
            val flatNode = WeightedGraph.Node(leaf.weight, leaf.value)
            flatByLeaf[leaf] = flatNode
            allFlatNodes.add(flatNode)
        }

        // Step 2: one contracted subgraph per child instance
        val flatByChildPort = IdentityHashMap<PortHierarchicalCircuitGraph.Node<N>, WeightedGraph.Node<N>>()

        graph.childInstances().forEach { instance ->
            val childResult = childResults[instance.childGraph]
            if (childResult == null) {
                Logger.error {
                    "Missing child solve result - child was not processed first " +
                        "(graph=${graph.value}, child=${instance.childGraph.value})"
                }
                return@run null
            }

            val expansion = buildChildExpansion(instance, childResult.summary, allFlatNodes, allFlatEdges)
            equalityConstraints.addAll(expansion.equalityConstraints)
            instance.ports.forEach { portNode ->
                flatByChildPort[portNode] = expansion.boundaryNodes.getValue(portNode.port)
            }
        }

        fun flatNodeFor(node: PortHierarchicalCircuitGraph.Node<N>): WeightedGraph.Node<N> = when (node) {
            is PortHierarchicalCircuitGraph.LeafNode<N> -> flatByLeaf.getValue(node)
            else -> flatByChildPort.getValue(node)
        }

        // Step 3: this module's own edges
        graph.edges.forEach { edge ->
            allFlatEdges.add(
                WeightedGraph.Edge(
                    weight = edge.weight,
                    source = flatNodeFor(edge.source),
                    sink = flatNodeFor(edge.sink),
                    value = edge.value,
                )
            )
        }

        // Step 4: a top-level module's own ports must stay mutually aligned - the environment gets
        // an interface where all inputs are consumed at one pipeline stage and all outputs produced
        // at another, rather than one where the caller has to skew them. Everything below the top is
        // free to skew, which is the entire point of this solver.
        if (isTopLevel) {
            graph.inputPorts.firstOrNull()?.let { reference ->
                graph.inputPorts.drop(1).forEach { port ->
                    equalityConstraints.add(NodeEqualityConstraint(flatByLeaf.getValue(reference), flatByLeaf.getValue(port), 0L))
                }
            }
            graph.outputPorts.firstOrNull()?.let { reference ->
                graph.outputPorts.drop(1).forEach { port ->
                    equalityConstraints.add(NodeEqualityConstraint(flatByLeaf.getValue(reference), flatByLeaf.getValue(port), 0L))
                }
            }
        }

        val flatGraph = LeisersonCircuitGraph(graph.value, allFlatNodes, allFlatEdges)

        // Step 5: solve. These graphs contain no VirtualIONodes, so MinimalRegisterSolver's own
        // boundary pinning contributes nothing - every boundary constraint here is explicit.
        val minimalRegisterSolver = MinimalRegisterSolver(MonolithicRetimingProblem(flatGraph), equalityConstraints, edgeSourceBits)
        val minimalResult = minimalRegisterSolver.solveOrNull(targetClockPeriod)
        if (minimalResult == null) {
            Logger.debug {
                "MinimalRegisterSolver infeasible for graph=${graph.value} at period=$targetClockPeriod " +
                    "(nodes=${allFlatNodes.size}, edges=${allFlatEdges.size}, constraints=${equalityConstraints.size})"
            }
            return@run null
        }
        val nodeLags = minimalRegisterSolver.lastSolveNodeLags
            ?: throw IllegalStateException("MinimalRegisterSolver returned a solution without recording its retiming labels")

        // Step 6: rebuild this graph with retimed weights, pointing children at their retimed graphs.
        // Port LeafNodes are unchanged by retiming (only edge weights move), so a ChildPortNode only
        // needs its childGraph swapped - its `port` still identifies the same node in the new graph.
        val retimedChildGraphs = IdentityHashMap<PortHierarchicalCircuitGraph<G, N, E>, PortHierarchicalCircuitGraph<G, N, E>>()
        graph.childInstances().forEach { instance ->
            retimedChildGraphs[instance.childGraph] = childResults.getValue(instance.childGraph).retimedGraph
        }

        fun retimedNodeFor(node: PortHierarchicalCircuitGraph.Node<N>): PortHierarchicalCircuitGraph.Node<N> =
            when (node) {
                is PortHierarchicalCircuitGraph.ChildPortNode<*, *, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val childPortNode = node as PortHierarchicalCircuitGraph.ChildPortNode<G, N, E>
                    childPortNode.copy(childGraph = retimedChildGraphs.getValue(childPortNode.childGraph))
                }
                else -> node
            }

        val retimedEdges = graph.edges.map { edge ->
            edge.copy(
                weight = edge.weight +
                    nodeLags.getValue(flatNodeFor(edge.sink)) -
                    nodeLags.getValue(flatNodeFor(edge.source)),
                source = retimedNodeFor(edge.source),
                sink = retimedNodeFor(edge.sink),
            )
        }

        val retimedGraph = PortHierarchicalCircuitGraph(
            value = graph.value,
            nodes = graph.nodes.map { retimedNodeFor(it) },
            edges = retimedEdges,
            inputPorts = graph.inputPorts,
            outputPorts = graph.outputPorts,
        )

        // Step 7: this module's own boundary summary, measured on the retimed flat graph
        val summary = computePortBoundarySummary(
            graph = graph,
            retimedFlatGraph = minimalResult.graph,
            flatByLeaf = flatByLeaf,
            nodeLags = nodeLags,
            ownRegisterCount = retimedEdges.sumOf { it.weight },
        )

        Logger.debug {
            "Boundary summary for graph=${graph.value}: " +
                graph.inputPorts.joinToString(prefix = "inputs[", postfix = "] ") {
                    "${it.value}: lag=${summary.portLags[it]} delay=${summary.inputDelays[it]}"
                } +
                graph.outputPorts.joinToString(prefix = "outputs[", postfix = "]") {
                    "${it.value}: lag=${summary.portLags[it]} delay=${summary.outputDelays[it]}"
                }
        }

        SolveResult(retimedGraph, summary)
    }

    /**
     * The contracted subgraph standing in for one already-solved child instance.
     *
     * Node weights are the child's per-port delays; edge weights are given by what they must be
     * *after* retiming, and are converted to starting weights by subtracting the lag difference the
     * equality constraints below will impose. That conversion collapses to the child's pre-retiming
     * port-pair register count on the two pair edges, and to zero on the rest.
     */
    private fun buildChildExpansion(
        instance: PortHierarchicalCircuitGraph.ChildInstance<G, N, E>,
        summary: PortBoundarySummary<N>,
        allFlatNodes: MutableList<WeightedGraph.Node<N>>,
        allFlatEdges: MutableList<WeightedGraph.Edge<N, E>>,
    ): ChildExpansion<N> {
        val constraints = mutableListOf<NodeEqualityConstraint<N>>()
        val boundaryNodes = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, WeightedGraph.Node<N>>()

        fun newNode(weight: Int): WeightedGraph.Node<N> =
            WeightedGraph.Node(weight, expansionNodeFactory()).also { allFlatNodes.add(it) }

        fun addEdge(weight: Int, source: WeightedGraph.Node<N>, sink: WeightedGraph.Node<N>) {
            allFlatEdges.add(WeightedGraph.Edge(weight, source, sink, expansionEdgeValueFactory()))
        }

        fun lagOf(port: PortHierarchicalCircuitGraph.LeafNode<N>) = summary.portLags.getValue(port)

        val inputPorts = instance.ports.filter { it.isInput }.map { it.port }
        val outputPorts = instance.ports.filterNot { it.isInput }.map { it.port }

        (inputPorts + outputPorts).forEach { port -> boundaryNodes[port] = newNode(0) }

        // Relative port lags - but only between ports the child actually relates. Within a component
        // the difference is forced by the child's own (already fixed) internal register counts;
        // across components it is arbitrary, so constraining it would invent a requirement the
        // hardware does not have. Ports are visited in declaration order so the reference port, and
        // therefore the emitted constraints, are deterministic.
        val orderedPorts = inputPorts + outputPorts
        orderedPorts
            .groupBy { summary.portComponents[it] ?: -1 }
            .forEach { (_, componentPorts) ->
                val referencePort = componentPorts.first()
                componentPorts.drop(1).forEach { port ->
                    constraints.add(
                        NodeEqualityConstraint(
                            source = boundaryNodes.getValue(referencePort),
                            sink = boundaryNodes.getValue(port),
                            value = (lagOf(port) - lagOf(referencePort)).toLong(),
                        )
                    )
                }
            }

        // A path from this port into the child that ends at a register. Deliberately left dangling:
        // nothing forces it to concatenate with an output-side path unless the two genuinely belong
        // to the same connected port pair, which is what the pair edges below express.
        val inputDelayNodes = inputPorts.associateWithIdentity { port ->
            newNode(summary.inputDelays[port] ?: 0).also { node ->
                addEdge(0, boundaryNodes.getValue(port), node)
                constraints.add(NodeEqualityConstraint(boundaryNodes.getValue(port), node, 0L))
            }
        }

        val outputDelayNodes = outputPorts.associateWithIdentity { port ->
            newNode(summary.outputDelays[port] ?: 0).also { node ->
                addEdge(0, node, boundaryNodes.getValue(port))
                constraints.add(NodeEqualityConstraint(boundaryNodes.getValue(port), node, 0L))
            }
        }

        inputPorts.forEach { inputPort ->
            outputPorts.forEach { outputPort ->
                val pair = PortPair(inputPort, outputPort)
                val registers = summary.pairRegisters[pair] ?: return@forEach
                val lagDifference = lagOf(outputPort) - lagOf(inputPort)

                val combinationalDelay = summary.pairCombinationalDelays[pair]
                if (combinationalDelay != null) {
                    // A genuinely combinational port pair, and the only thing that can close a
                    // zero-weight cycle through this instance - which is correct, because such a
                    // cycle is then a real combinational loop.
                    val node = newNode(combinationalDelay)
                    addEdge(registers - lagDifference, boundaryNodes.getValue(inputPort), node)
                    addEdge(0, node, boundaryNodes.getValue(outputPort))
                    constraints.add(NodeEqualityConstraint(boundaryNodes.getValue(outputPort), node, 0L))
                } else {
                    addEdge(
                        registers - lagDifference,
                        inputDelayNodes.getValue(inputPort),
                        outputDelayNodes.getValue(outputPort),
                    )
                }
            }
        }

        return ChildExpansion(boundaryNodes, constraints)
    }

    private fun computePortBoundarySummary(
        graph: PortHierarchicalCircuitGraph<G, N, E>,
        retimedFlatGraph: LeisersonCircuitGraph<G, N, E>,
        flatByLeaf: Map<PortHierarchicalCircuitGraph.LeafNode<N>, WeightedGraph.Node<N>>,
        nodeLags: Map<WeightedGraph.Node<N>, Int>,
        ownRegisterCount: Int,
    ): PortBoundarySummary<N> {
        val portLags = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, Int>()
        val inputDelays = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, Int>()
        val outputDelays = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, Int>()
        val pairRegisters = mutableMapOf<PortPair<N>, Int>()
        val pairCombinationalDelays = mutableMapOf<PortPair<N>, Int>()

        (graph.inputPorts + graph.outputPorts).forEach { port ->
            portLags[port] = nodeLags.getValue(flatByLeaf.getValue(port))
        }

        val outputFlatNodes = graph.outputPorts.map { flatByLeaf.getValue(it) }

        graph.inputPorts.forEach { inputPort ->
            val connections = retimedFlatGraph.findFastestConnectionsFromNode(flatByLeaf.getValue(inputPort))

            // The longest combinational path starting at this port and ending anywhere other than an
            // output port - i.e. ending at a register.
            inputDelays[inputPort] = connections
                .filter { connection -> outputFlatNodes.none { it === connection.sink } }
                .filter { it.registerCount == 0 }
                .maxOfOrNull { it.delay } ?: 0

            graph.outputPorts.forEach { outputPort ->
                val outputFlatNode = flatByLeaf.getValue(outputPort)
                val connection = connections.firstOrNull { it.sink === outputFlatNode } ?: return@forEach
                val pair = PortPair(inputPort, outputPort)
                pairRegisters[pair] = connection.registerCount
                if (connection.registerCount == 0) pairCombinationalDelays[pair] = connection.delay
            }
        }

        val reversedGraph = LeisersonCircuitGraph(
            value = retimedFlatGraph.value,
            nodes = retimedFlatGraph.nodes,
            edges = retimedFlatGraph.edges.map { WeightedGraph.Edge(it.weight, it.sink, it.source, it.value) },
        )
        val inputFlatNodes = graph.inputPorts.map { flatByLeaf.getValue(it) }

        graph.outputPorts.forEach { outputPort ->
            outputDelays[outputPort] = reversedGraph
                .findFastestConnectionsFromNode(flatByLeaf.getValue(outputPort))
                .filter { connection -> inputFlatNodes.none { it === connection.sink } }
                .filter { it.registerCount == 0 }
                .maxOfOrNull { it.delay } ?: 0
        }

        return PortBoundarySummary(
            portLags = portLags,
            inputDelays = inputDelays,
            outputDelays = outputDelays,
            pairRegisters = pairRegisters,
            pairCombinationalDelays = pairCombinationalDelays,
            portComponents = computePortComponents(graph, retimedFlatGraph, flatByLeaf),
            clockPeriod = retimedFlatGraph.computeClockPeriod(),
            ownRegisterCount = ownRegisterCount,
        )
    }

    /**
     * Assigns each of [graph]'s ports the id of its weakly-connected component in the flat graph.
     *
     * Weak, not strong, connectivity: a lag difference is pinned down by any chain of edges between
     * two ports regardless of direction, since `w_r = w + r(sink) - r(source)` constrains both ends
     * of every edge. Ports with no such chain between them have a genuinely free relative lag.
     */
    private fun computePortComponents(
        graph: PortHierarchicalCircuitGraph<G, N, E>,
        flatGraph: LeisersonCircuitGraph<G, N, E>,
        flatByLeaf: Map<PortHierarchicalCircuitGraph.LeafNode<N>, WeightedGraph.Node<N>>,
    ): Map<PortHierarchicalCircuitGraph.LeafNode<N>, Int> {
        val parent = IdentityHashMap<WeightedGraph.Node<N>, WeightedGraph.Node<N>>()

        fun find(node: WeightedGraph.Node<N>): WeightedGraph.Node<N> {
            var root = node
            while (parent[root] != null && parent[root] !== root) root = parent.getValue(root)
            parent[node] = root
            return root
        }

        flatGraph.nodes.forEach { parent[it] = it }
        flatGraph.edges.forEach { edge ->
            val sourceRoot = find(edge.source)
            val sinkRoot = find(edge.sink)
            if (sourceRoot !== sinkRoot) parent[sourceRoot] = sinkRoot
        }

        val componentIds = IdentityHashMap<WeightedGraph.Node<N>, Int>()
        val result = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, Int>()

        (graph.inputPorts + graph.outputPorts).forEach { port ->
            val root = find(flatByLeaf.getValue(port))
            result[port] = componentIds.getOrPut(root) { componentIds.size }
        }

        return result
    }
}

private fun <T> Collection<T>.toIdentitySet(): Set<T> =
    java.util.Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(this@toIdentitySet) }

private fun <K, V> Collection<K>.associateWithIdentity(valueSelector: (K) -> V): Map<K, V> =
    IdentityHashMap<K, V>().apply { this@associateWithIdentity.forEach { put(it, valueSelector(it)) } }
