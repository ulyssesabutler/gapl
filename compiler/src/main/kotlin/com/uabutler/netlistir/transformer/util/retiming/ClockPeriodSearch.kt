package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.netlistir.transformer.util.retiming.solver.Solver
import com.uabutler.util.Logger

fun <P : RetimingProblem> findMinimumClockPeriod(
    solver: Solver<P>, problem: P
): Int = Logger.run("Finding minimum clock period") {
    val possibleClockPeriods = Logger.run("Finding possible clock periods") {
        problem.computePossibleClockPeriods().also {
            Logger.debug { "Found ${it.size} possible clock periods" }
            Logger.debug { it.joinToString() }
        }
    }

    val cache = mutableMapOf<Int, Boolean>()
    fun attempt(clockPeriod: Int): Boolean {
        if (cache.containsKey(clockPeriod)) {
            Logger.debug { "Clock period $clockPeriod was already checked" }
            return cache[clockPeriod]!!
        }

        val result = solver.solveOrNull(clockPeriod)

        if (result != null) {
            Logger.debug { "Clock period $clockPeriod is feasible" }

            possibleClockPeriods
                .filter { it >= result.computeClockPeriod() }
                .forEach { cache[it] = true }

            return true
        } else {
            Logger.debug { "Clock period $clockPeriod is infeasible" }
            return false
        }
    }

    possibleClockPeriods.sorted().binarySearch { clockPeriod -> if (attempt(clockPeriod)) 1 else -1 }

    return@run cache.filter { it.value }.minBy { it.key }.key
}
