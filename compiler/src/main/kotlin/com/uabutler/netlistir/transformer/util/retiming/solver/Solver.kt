package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.RetimingProblem

abstract class Solver<P : RetimingProblem>(open val problem: P) {
    abstract fun solveOrNull(
        targetClockPeriod: Int?,
    ): P?
}
