package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.util.graph.PortHierarchicalCircuitGraph
import java.util.IdentityHashMap

/**
 * The per-port counterpart of [HierarchicalRetimingProblem].
 *
 * [graphs] is every module in the batch, not only the top-level ones - the solver needs them all,
 * and its all-or-nothing contract is defined over the whole set. Clock-period queries, though, only
 * flatten the *top-level* graphs: flattening inlines children recursively, so a child's own paths
 * are already covered by its parent's flattening, and flattening it again separately is pure waste.
 *
 * As with [HierarchicalRetimingProblem], these queries are only safe on a pristine, never-retimed
 * problem.
 */
class PortHierarchicalRetimingProblem<G, N, E>(
    graphs: Collection<PortHierarchicalCircuitGraph<G, N, E>>,
) : RetimingProblem() {
    val graphs: List<PortHierarchicalCircuitGraph<G, N, E>> = graphs.toList()

    /** The graphs no other graph in this batch instantiates. */
    val topLevelGraphs: List<PortHierarchicalCircuitGraph<G, N, E>> by lazy {
        val instantiated = IdentityHashMap<PortHierarchicalCircuitGraph<G, N, E>, Boolean>()
        this.graphs.forEach { graph ->
            graph.childInstances().forEach { instantiated[it.childGraph] = true }
        }
        this.graphs.filter { instantiated[it] != true }
    }

    override fun computeClockPeriod() = topLevelGraphs.maxOf { it.flatten().computeClockPeriod() }

    override fun computePossibleClockPeriods() =
        topLevelGraphs.flatMap { it.flatten().computePossibleClockPeriods() }.toSet()
}
