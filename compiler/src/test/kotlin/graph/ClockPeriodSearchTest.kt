package graph

import com.uabutler.RetimingInfeasibleException
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.findMinimumClockPeriod
import com.uabutler.netlistir.transformer.util.retiming.solver.MonolithicSolver
import com.uabutler.util.Logger
import graph.TestUtil.createGraph
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClockPeriodSearchTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    /** A solver that reports [feasibleFrom] and everything above it as achievable. */
    private class ThresholdSolver(
        problem: MonolithicRetimingProblem<String, String, String>,
        private val feasibleFrom: Int?,
    ) : MonolithicSolver<String, String, String>(problem) {
        override fun solveOrNull(targetClockPeriod: Int?): MonolithicRetimingProblem<String, String, String>? =
            if (feasibleFrom != null && targetClockPeriod != null && targetClockPeriod >= feasibleFrom) problem else null
    }

    private fun chainProblem(): MonolithicRetimingProblem<String, String, String> =
        MonolithicRetimingProblem(
            createGraph(
                name = "chain",
                edgeList = listOf(
                    EdgeSketch("a", "b", 0),
                    EdgeSketch("b", "c", 0),
                    EdgeSketch("c", "d", 0),
                ),
            )
        )

    @Test
    fun `finds the smallest feasible candidate period`() {
        val problem = chainProblem()
        // Unit node weights along a 4-node chain, so the candidates are 1..4.
        assertEquals(setOf(1, 2, 3, 4), problem.computePossibleClockPeriods())

        assertEquals(3, findMinimumClockPeriod(ThresholdSolver(problem, feasibleFrom = 3), problem))
    }

    /**
     * "Nothing is achievable" has to surface as a retiming failure, not as an internal error.
     *
     * It cannot happen for a monolithic problem - the largest candidate is the unretimed critical
     * path, which is always achievable - but it can for a hierarchical one, whose candidate periods
     * come from the fully expanded graph while feasibility is judged against each module's
     * (strictly more conservative) contracted graph. Taking the minimum of an empty set of confirmed
     * periods used to throw NoSuchElementException straight out to the CLI's "contact a TA" handler.
     */
    @Test
    fun `no feasible candidate period reports a retiming failure`() {
        val problem = chainProblem()

        assertFailsWith<RetimingInfeasibleException> {
            findMinimumClockPeriod(ThresholdSolver(problem, feasibleFrom = null), problem)
        }
    }
}
