package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem

abstract class MonolithicSolver<G, N, E>(
    problem: MonolithicRetimingProblem<G, N, E>,
) : Solver<MonolithicRetimingProblem<G, N, E>>(problem)
