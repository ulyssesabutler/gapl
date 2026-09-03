package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.util.Logger
import com.uabutler.util.graph.PortHierarchicalCircuitGraph
import java.util.IdentityHashMap

/**
 * Checks a finished per-port hierarchical retiming against plain Leiserson-Saxe, by flattening the
 * whole design and deriving one monolithic lag per node from the per-module lags.
 *
 * The point is to check the *result* rather than trusting the summaries. Every existing check in
 * this solver compares one module's numbers against another module's numbers, so a summary that
 * misdescribes its own module is invisible to all of them - and that is exactly the failure that
 * has shown up in practice.
 *
 * ## The mapping
 *
 * A retiming is only defined up to an additive constant, so each module's `r_M` lives in its own
 * frame. Embedding a child instance into its parent's frame is a single offset:
 *
 *     offset(instance) = r_parent(boundary node of port p) - r_child(p)      for any port p
 *     R(instance, v)   = r_child(v) + offset(instance)                       for every node v
 *
 * One offset per *port component*, not per instance: a module whose ports fall into several
 * [PortBoundarySummary.portComponents] has no single frame, because the parent is deliberately free
 * to place unrelated components at unrelated lags, and forcing them into one frame would invent a
 * requirement the hardware does not have.
 *
 * ## What can and cannot fail
 *
 * Worth stating, because it decides where the checks belong. A per-edge Leiserson check passes *by
 * construction*: for an edge inside a module both endpoints share a component, the offsets cancel,
 * and what is left is `w(e) + r_M(v) - r_M(u)` - the module's own solved value, non-negative
 * because its own ILP said so. For an edge crossing into a child, substituting the offset
 * definition collapses it to the parent's own solved value the same way. Per-cycle conservation
 * follows for the same reason. So no amount of edge-level checking can find anything, which is
 * precisely why every local check in this solver has always come back clean.
 *
 * What is *not* automatic is the relationship between a child's summary and the child's actual
 * internals. The parent models a child port pair as one edge carrying
 * [PortBoundarySummary.pairRegisters], which `computePortBoundarySummary` takes from
 * `findFastestConnectionsFromNode` - a shortest-path search, so a **minimum** over paths. Flattened,
 * the child has many paths, and in a combinational region every one of them satisfies
 * `w_r(path) = R(o) - R(i)`, so they must all be equal. If the emitted hardware disagrees, the
 * derived lags cannot reproduce it, and that contradiction is [checkInstanceBalance].
 */
internal class PerPortHierarchicalRetimingVerifier<G, N, E>(
    private val results: Map<PortHierarchicalCircuitGraph<G, N, E>, PerPortHierarchicalMinimalRegisterSolver.SolveResult<G, N, E>>,
    private val targetClockPeriod: Int,
) {

    /** A node of the flattened design: one instance path plus one module-local leaf node. */
    private class FlatNode<N>(
        val leaf: PortHierarchicalCircuitGraph.LeafNode<N>,
        val path: String,
        val lag: Int,
    ) {
        val outgoing = mutableListOf<FlatEdge<N>>()
        val incoming = mutableListOf<FlatEdge<N>>()
    }

    private class FlatEdge<N>(
        val source: FlatNode<N>,
        val sink: FlatNode<N>,
        /** Weight in the *unretimed* design. */
        val originalWeight: Int,
    ) {
        /** `w(e) + R(sink) - R(source)`, the register count the derived monolithic lags imply. */
        val derivedWeight: Int get() = originalWeight + sink.lag - source.lag
    }

    private var findings = 0
    private var skipped = 0

    private companion object {
        /** Loops in real designs are tiny; anything larger is reported rather than enumerated. */
        const val SCC_NODE_LIMIT = 64
        const val SCC_PATH_LIMIT = 200_000
    }

    fun verify(topLevelGraphs: Collection<PortHierarchicalCircuitGraph<G, N, E>>) {
        Logger.run("Verifying per-port hierarchical retiming") {
            topLevelGraphs.forEach { top ->
                val result = results[top] ?: return@forEach
                val nodes = mutableListOf<FlatNode<N>>()
                val expansion = expand(top, offset = 0, path = moduleName(top), nodes = nodes)
                    ?: return@forEach

                checkLegality(nodes, moduleName(top))
                checkClockPeriod(nodes, moduleName(top))
                // The top module is nobody's child, so the per-instance pass never reaches it - and
                // it is precisely where a data path and the sideband chain that is supposed to track
                // it both run from `i` to `o`.
                checkBalance(
                    label = moduleName(top),
                    moduleLabel = moduleName(top),
                    inputPorts = top.inputPorts,
                    outputPorts = top.outputPorts,
                    ports = expansion,
                    subtree = nodes,
                    summary = result.summary,
                )
            }
            Logger.info { "Retiming verification finished with $findings finding(s), $skipped port(s) skipped" }
        }
    }

    /**
     * Instantiates [graph] into the flattened design at [offset], recursing into each child
     * instance, and returns this instance's own port nodes. Null if the module was never solved.
     */
    private fun expand(
        graph: PortHierarchicalCircuitGraph<G, N, E>,
        offset: Int,
        path: String,
        nodes: MutableList<FlatNode<N>>,
    ): Map<PortHierarchicalCircuitGraph.LeafNode<N>, FlatNode<N>>? {
        val result = results[graph] ?: return null

        val flatByLeaf = IdentityHashMap<PortHierarchicalCircuitGraph.LeafNode<N>, FlatNode<N>>()
        graph.nodes.filterIsInstance<PortHierarchicalCircuitGraph.LeafNode<N>>().forEach { leaf ->
            val lag = result.leafLags[leaf]
            if (lag != null) {
                FlatNode(leaf, path, lag + offset).also {
                    flatByLeaf[leaf] = it
                    nodes.add(it)
                }
            }
        }

        val flatByChildPort = IdentityHashMap<PortHierarchicalCircuitGraph.Node<N>, FlatNode<N>>()

        graph.childInstances().forEach { instance ->
            val childResult = results[instance.childGraph] ?: return@forEach
            val childPath = "$path/${nodeName(instance.value)}"

            // One offset per port component. Every port in a component must agree on it - that is
            // the well-definedness of the embedding, and the first contradiction to look for.
            val offsetByComponent = mutableMapOf<Int, Int>()
            instance.ports.forEach { portNode ->
                val childLag = childResult.summary.portLags[portNode.port] ?: return@forEach
                val parentLag = result.childPortLags[portNode] ?: return@forEach
                val component = childResult.summary.portComponents[portNode.port] ?: -1
                val implied = parentLag + offset - childLag

                val existing = offsetByComponent[component]
                if (existing == null) {
                    offsetByComponent[component] = implied
                } else if (existing != implied) {
                    findings++
                    Logger.error {
                        "Retiming frame contradiction at $childPath (${moduleName(instance.childGraph)}): " +
                            "port ${nodeName(portNode.port.value)} implies offset $implied, but another " +
                            "port of the same component implies $existing. The child's internal lags " +
                            "cannot be embedded in the parent's frame, so no monolithic retiming " +
                            "reproduces this circuit."
                    }
                }
            }

            val childNodesStart = nodes.size
            val childOffset = offsetByComponent.values.firstOrNull() ?: offset
            val childPorts = expand(instance.childGraph, childOffset, childPath, nodes) ?: return@forEach

            instance.ports.forEach { portNode ->
                childPorts[portNode.port]?.let { flatByChildPort[portNode] = it }
            }

            checkInstanceBalance(
                instance = instance,
                summary = childResult.summary,
                childPorts = childPorts,
                subtree = nodes.subList(childNodesStart, nodes.size),
                path = childPath,
            )
        }

        fun resolve(node: PortHierarchicalCircuitGraph.Node<N>): FlatNode<N>? = when (node) {
            is PortHierarchicalCircuitGraph.LeafNode<N> -> flatByLeaf[node]
            else -> flatByChildPort[node]
        }

        graph.edges.forEach { edge ->
            val source = resolve(edge.source) ?: return@forEach
            val sink = resolve(edge.sink) ?: return@forEach
            FlatEdge(source, sink, edge.weight).also {
                source.outgoing.add(it)
                sink.incoming.add(it)
            }
        }

        return flatByLeaf
    }

    /**
     * The check that can actually fire: within one instance, every path between a port pair must
     * carry the same number of registers, and that number must be what the child told its parent.
     *
     * An unbalanced pair is wrong hardware on its own terms - the output mixes beat `T-n` arriving
     * on one path with beat `T-n-1` on another - *and* it is mis-reported upward, because
     * `pairRegisters` is a minimum over paths.
     */
    private fun checkInstanceBalance(
        instance: PortHierarchicalCircuitGraph.ChildInstance<G, N, E>,
        summary: PortBoundarySummary<N>,
        childPorts: Map<PortHierarchicalCircuitGraph.LeafNode<N>, FlatNode<N>>,
        subtree: List<FlatNode<N>>,
        path: String,
    ) = checkBalance(
        label = path,
        moduleLabel = moduleName(instance.childGraph),
        inputPorts = instance.ports.filter { it.isInput }.map { it.port },
        outputPorts = instance.ports.filterNot { it.isInput }.map { it.port },
        ports = childPorts,
        subtree = subtree,
        summary = summary,
    )

    private fun checkBalance(
        label: String,
        moduleLabel: String,
        inputPorts: List<PortHierarchicalCircuitGraph.LeafNode<N>>,
        outputPorts: List<PortHierarchicalCircuitGraph.LeafNode<N>>,
        ports: Map<PortHierarchicalCircuitGraph.LeafNode<N>, FlatNode<N>>,
        subtree: List<FlatNode<N>>,
        summary: PortBoundarySummary<N>,
    ) {
        val extremes = PathExtremes(subtree)

        inputPorts.forEach { inputPort ->
            val source = ports[inputPort] ?: return@forEach
            val reachable = extremes.from(source)

            outputPorts.forEach { outputPort ->
                val sink = ports[outputPort] ?: return@forEach
                val (min, max, approximated) = reachable[sink] ?: return@forEach

                if (approximated) {
                    skipped++
                    Logger.info {
                        "Balance check inconclusive at $label ($moduleLabel): " +
                            "${nodeName(inputPort.value)} -> ${nodeName(outputPort.value)} crosses a feedback " +
                            "loop too large to enumerate exactly."
                    }
                    return@forEach
                }

                Logger.info {
                    "pair $label ($moduleLabel) ${nodeName(inputPort.value)} -> " +
                        "${nodeName(outputPort.value)}: feed-forward $min..$max, module reports " +
                        "${summary.pairRegisters[PortPair(inputPort, outputPort)]}"
                }

                if (min != max) {
                    findings++
                    Logger.error {
                        "Unbalanced port pair at $label ($moduleLabel): " +
                            "${nodeName(inputPort.value)} -> ${nodeName(outputPort.value)} has paths of " +
                            "$min and $max registers. Its output mixes beats ${max - min} cycle(s) apart."
                    }
                }

                val claimed = summary.pairRegisters[PortPair(inputPort, outputPort)]
                if (claimed != null && claimed != max) {
                    findings++
                    Logger.error {
                        "Mis-reported port pair at $label ($moduleLabel): " +
                            "${nodeName(inputPort.value)} -> ${nodeName(outputPort.value)} takes $max " +
                            "register(s), but the module reports $claimed to its caller. Everything the " +
                            "caller sizes from that number - a sideband `valid` chain above all - is " +
                            "${max - claimed} stage(s) out."
                    }
                }
            }
        }
    }

    /**
     * Minimum and maximum register count from [source] to every node reachable from it.
     *
     * Feedback loops are the whole difficulty. A naive longest path is unbounded the moment a loop
     * is reachable, and in practice one always is - bloom-filter's state register, CMS's counters -
     * so an earlier version guarded by bailing out and silently skipped every module worth checking.
     * Collapsing each SCC to a zero-weight supernode was the next attempt, but that *under-counts*
     * any register absorbed inside the loop, which produces confident wrong answers: it reported
     * bloom_filter's `valid -> o` as taking 0 registers against a claimed 1, purely as an artifact.
     *
     * So each SCC is measured rather than collapsed. Between components the DP is an ordinary
     * topological relaxation; inside one, every *simple* path from each entry node is enumerated,
     * which is the well-defined notion here - one beat traverses the loop region once, and going
     * round again is a later beat, not a longer path for this one. Enumeration is exponential in
     * principle, so an SCC bigger than [SCC_NODE_LIMIT] nodes, or one that exceeds
     * [SCC_PATH_LIMIT] steps, marks its results inconclusive instead of guessing. Real loops here
     * are a handful of nodes - bloom's is a state register, a mux and the child's port nodes.
     */
    private inner class PathExtremes(scope: List<FlatNode<N>>) {
        private val component = IdentityHashMap<FlatNode<N>, Int>()
        private val members = mutableListOf<MutableList<FlatNode<N>>>()

        init {
            tarjan(scope)
        }

        fun componentOf(node: FlatNode<N>): Int? = component[node]

        /** A component that is a real loop, not a single node. */
        fun isCyclic(comp: Int): Boolean =
            members[comp].size > 1 || members[comp].any { node -> node.outgoing.any { it.sink === node } }

        /** Iterative Tarjan - the flattened design is far too deep for recursion. */
        private fun tarjan(scope: List<FlatNode<N>>) {
            val inScope = java.util.Collections.newSetFromMap(IdentityHashMap<FlatNode<N>, Boolean>())
            inScope.addAll(scope)

            val index = IdentityHashMap<FlatNode<N>, Int>()
            val low = IdentityHashMap<FlatNode<N>, Int>()
            val onStack = java.util.Collections.newSetFromMap(IdentityHashMap<FlatNode<N>, Boolean>())
            val stack = ArrayDeque<FlatNode<N>>()
            var counter = 0

            scope.forEach { root ->
                if (index.containsKey(root)) return@forEach
                val work = ArrayDeque<Pair<FlatNode<N>, Int>>()
                work.addLast(root to 0)

                while (work.isNotEmpty()) {
                    val (node, edgeIndex) = work.removeLast()
                    if (edgeIndex == 0) {
                        index[node] = counter
                        low[node] = counter
                        counter++
                        stack.addLast(node)
                        onStack.add(node)
                    }

                    var i = edgeIndex
                    var descended = false
                    while (i < node.outgoing.size) {
                        val next = node.outgoing[i].sink
                        i++
                        if (next !in inScope) continue
                        if (!index.containsKey(next)) {
                            work.addLast(node to i)
                            work.addLast(next to 0)
                            descended = true
                            break
                        } else if (next in onStack) {
                            low[node] = minOf(low.getValue(node), index.getValue(next))
                        }
                    }
                    if (descended) continue

                    if (low.getValue(node) == index.getValue(node)) {
                        val group = mutableListOf<FlatNode<N>>()
                        while (true) {
                            val popped = stack.removeLast()
                            onStack.remove(popped)
                            component[popped] = members.size
                            group.add(popped)
                            if (popped === node) break
                        }
                        members.add(group)
                    }

                    work.lastOrNull()?.let { (parent, _) ->
                        low[parent] = minOf(low.getValue(parent), low.getValue(node))
                    }
                }
            }
        }

        /**
         * Min/max registers from [source] to each reachable node, plus whether the figure had to be
         * approximated because an SCC on the way was too large to enumerate.
         */
        fun from(source: FlatNode<N>): Map<FlatNode<N>, Triple<Int, Int, Boolean>> {
            val root = component[source] ?: return emptyMap()

            // Reachable components, and the inter-component edges between them.
            val outgoing = mutableMapOf<Int, MutableList<FlatEdge<N>>>()
            val reachable = mutableSetOf(root)
            val pending = ArrayDeque<Int>().apply { add(root) }
            while (pending.isNotEmpty()) {
                val comp = pending.removeLast()
                members[comp].forEach { node ->
                    node.outgoing.forEach { edge ->
                        val target = component[edge.sink] ?: return@forEach
                        if (target == comp) return@forEach
                        outgoing.getOrPut(comp) { mutableListOf() }.add(edge)
                        if (reachable.add(target)) pending.add(target)
                    }
                }
            }

            val inDegree = reachable.associateWith { 0 }.toMutableMap()
            reachable.forEach { comp ->
                outgoing[comp]?.forEach { edge ->
                    val target = component.getValue(edge.sink)
                    inDegree[target] = inDegree.getValue(target) + 1
                }
            }
            val order = mutableListOf<Int>()
            val ready = ArrayDeque(reachable.filter { inDegree.getValue(it) == 0 })
            while (ready.isNotEmpty()) {
                val comp = ready.removeFirst()
                order.add(comp)
                outgoing[comp]?.forEach { edge ->
                    val target = component.getValue(edge.sink)
                    val remaining = inDegree.getValue(target) - 1
                    inDegree[target] = remaining
                    if (remaining == 0) ready.add(target)
                }
            }

            val best = IdentityHashMap<FlatNode<N>, Triple<Int, Int, Boolean>>()
            var loopCarried = 0
            val entries = mutableMapOf<Int, IdentityHashMap<FlatNode<N>, Triple<Int, Int, Boolean>>>()
            entries[root] = IdentityHashMap<FlatNode<N>, Triple<Int, Int, Boolean>>().apply {
                put(source, Triple(0, 0, false))
            }

            fun merge(
                into: MutableMap<FlatNode<N>, Triple<Int, Int, Boolean>>,
                node: FlatNode<N>,
                value: Triple<Int, Int, Boolean>,
            ) {
                val existing = into[node]
                into[node] = if (existing == null) value
                else Triple(
                    minOf(existing.first, value.first),
                    maxOf(existing.second, value.second),
                    existing.third || value.third,
                )
            }

            order.forEach { comp ->
                val entry = entries[comp] ?: return@forEach
                val group = members[comp]

                // A cyclic component is where the design keeps state, and a path through it is a
                // *previous* beat reaching the output - legitimate, and not a latency for this beat.
                // Measuring it as one produced confident false positives twice: first by collapsing
                // the loop to zero weight, then by enumerating round it. So loop-carried paths are
                // dropped, and what remains is the feed-forward latency the caller actually sizes
                // its sideband from.
                if (isCyclic(comp)) {
                    loopCarried += entry.size
                    return@forEach
                }
                entry.forEach { (node, value) -> merge(best, node, value) }

                group.forEach { node ->
                    val value = best[node] ?: return@forEach
                    node.outgoing.forEach { edge ->
                        val target = component[edge.sink] ?: return@forEach
                        if (target == comp) return@forEach
                        merge(
                            entries.getOrPut(target) { IdentityHashMap() },
                            edge.sink,
                            Triple(value.first + edge.derivedWeight, value.second + edge.derivedWeight, value.third),
                        )
                    }
                }
            }

            return best
        }

        /**
         * Min/max registers over every simple path from [start] to each node of one SCC, or null if
         * the enumeration exceeds [SCC_PATH_LIMIT].
         */
        private fun simplePaths(
            start: FlatNode<N>,
            group: Set<FlatNode<N>>,
        ): Map<FlatNode<N>, Pair<Int, Int>>? {
            val result = IdentityHashMap<FlatNode<N>, Pair<Int, Int>>()
            val visited = java.util.Collections.newSetFromMap(IdentityHashMap<FlatNode<N>, Boolean>())
            var steps = 0

            fun walk(node: FlatNode<N>, accumulated: Int): Boolean {
                if (++steps > SCC_PATH_LIMIT) return false
                val existing = result[node]
                result[node] = if (existing == null) accumulated to accumulated
                else minOf(existing.first, accumulated) to maxOf(existing.second, accumulated)

                node.outgoing.forEach { edge ->
                    val next = edge.sink
                    if (next !in group || !visited.add(next)) return@forEach
                    if (!walk(next, accumulated + edge.derivedWeight)) return false
                    visited.remove(next)
                }
                return true
            }

            visited.add(start)
            return if (walk(start, 0)) result else null
        }
    }

    /** Plain Leiserson legality on the flattened design: no edge may carry a negative weight. */
    private fun checkLegality(nodes: List<FlatNode<N>>, top: String) {
        var negative = 0
        var worst: FlatEdge<N>? = null
        nodes.forEach { node ->
            node.outgoing.forEach { edge ->
                if (edge.derivedWeight < 0) {
                    negative++
                    if (worst == null || edge.derivedWeight < worst!!.derivedWeight) worst = edge
                }
            }
        }
        if (negative > 0) {
            findings++
            val edge = worst!!
            Logger.error {
                "Illegal derived retiming in $top: $negative edge(s) carry a negative register count, " +
                    "worst ${edge.derivedWeight} on ${edge.source.path}:${nodeName(edge.source.leaf.value)} -> " +
                    "${edge.sink.path}:${nodeName(edge.sink.leaf.value)}."
            }
        }
    }

    /**
     * Clock period across the whole flattened design.
     *
     * Distinct from anything the per-module solves check: each module verifies its own period
     * against its children's summarised `inputDelays`/`outputDelays`, so a combinational path that
     * spans a module boundary is only ever seen through those summaries, never end to end.
     */
    private fun checkClockPeriod(nodes: List<FlatNode<N>>, top: String) {
        val inDegree = IdentityHashMap<FlatNode<N>, Int>()
        nodes.forEach { inDegree[it] = 0 }
        nodes.forEach { node ->
            node.outgoing.forEach { edge ->
                if (edge.derivedWeight == 0) inDegree[edge.sink] = (inDegree[edge.sink] ?: 0) + 1
            }
        }

        val delay = IdentityHashMap<FlatNode<N>, Int>()
        val ready = ArrayDeque(nodes.filter { inDegree.getValue(it) == 0 })
        var processed = 0
        var worst: FlatNode<N>? = null
        while (ready.isNotEmpty()) {
            val node = ready.removeFirst()
            processed++
            val here = node.leaf.weight + (delay[node] ?: 0)
            delay[node] = here
            if (worst == null || here > (delay[worst!!] ?: 0)) worst = node
            node.outgoing.forEach { edge ->
                if (edge.derivedWeight != 0) return@forEach
                delay[edge.sink] = maxOf(delay[edge.sink] ?: 0, here)
                val remaining = inDegree.getValue(edge.sink) - 1
                inDegree[edge.sink] = remaining
                if (remaining == 0) ready.add(edge.sink)
            }
        }

        if (processed != nodes.size) {
            findings++
            Logger.error {
                "Combinational loop in the flattened retiming of $top: ${nodes.size - processed} " +
                    "node(s) lie on a zero-register cycle."
            }
            return
        }

        val achieved = delay.values.maxOrNull() ?: 0
        if (achieved > targetClockPeriod) {
            findings++
            Logger.error {
                "Clock period violated in $top: the flattened design's longest combinational path is " +
                    "$achieved, above the target $targetClockPeriod. A path crossing a module boundary " +
                    "is only ever seen through per-port delay summaries, never end to end."
            }
        } else {
            Logger.info { "Flattened clock period for $top: $achieved (target $targetClockPeriod)" }
        }
    }

    private fun moduleName(graph: PortHierarchicalCircuitGraph<G, N, E>): String {
        val value = graph.value
        return if (value is com.uabutler.netlistir.netlist.MutableModule) value.invocation.gaplFunctionName
        else value.toString().take(40)
    }

    private fun nodeName(value: N): String =
        (value as? com.uabutler.netlistir.netlist.Node)?.name() ?: value.toString().take(40)
}
