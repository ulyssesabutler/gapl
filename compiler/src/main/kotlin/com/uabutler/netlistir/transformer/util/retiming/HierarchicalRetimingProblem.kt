package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph

class HierarchicalRetimingProblem<G, N, E>(
    roots: Collection<HierarchicalLeisersonCircuitGraph<G, N, E>>,
) : RetimingProblem() {
    val roots: List<HierarchicalLeisersonCircuitGraph<G, N, E>> = roots.toList()

    override fun computeClockPeriod() = roots.maxOf { it.flatten().computeClockPeriod() }
    override fun computePossibleClockPeriods() = roots.flatMap { it.flatten().computePossibleClockPeriods() }.toSet()
}
