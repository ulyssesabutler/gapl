package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.HierarchicalRetimingProblem

abstract class HierarchicalSolver<G, N, E>(
    problem: HierarchicalRetimingProblem<G, N, E>,
) : Solver<HierarchicalRetimingProblem<G, N, E>>(problem)
