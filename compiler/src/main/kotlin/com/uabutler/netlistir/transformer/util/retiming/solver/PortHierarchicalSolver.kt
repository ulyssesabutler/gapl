package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.PortHierarchicalRetimingProblem

abstract class PortHierarchicalSolver<G, N, E>(
    problem: PortHierarchicalRetimingProblem<G, N, E>,
) : Solver<PortHierarchicalRetimingProblem<G, N, E>>(problem)
