package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.util.graph.LeisersonCircuitGraph

abstract class Solver<G, N, E>(open val graph: LeisersonCircuitGraph<G, N, E>) {
    abstract fun solveOrNull(
        targetClockPeriod: Int?,
    ): LeisersonCircuitGraph<G, N, E>?
}