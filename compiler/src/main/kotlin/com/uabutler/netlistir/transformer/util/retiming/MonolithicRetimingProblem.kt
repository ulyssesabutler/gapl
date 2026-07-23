package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.util.graph.LeisersonCircuitGraph

class MonolithicRetimingProblem<G, N, E>(
    val graph: LeisersonCircuitGraph<G, N, E>,
) : RetimingProblem() {
    override fun computeClockPeriod() = graph.computeClockPeriod()
    override fun computePossibleClockPeriods() = graph.computePossibleClockPeriods()
}
