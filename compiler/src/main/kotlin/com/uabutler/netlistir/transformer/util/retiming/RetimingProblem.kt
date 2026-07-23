package com.uabutler.netlistir.transformer.util.retiming

abstract class RetimingProblem {
    abstract fun computeClockPeriod(): Int
    abstract fun computePossibleClockPeriods(): Set<Int>
}
