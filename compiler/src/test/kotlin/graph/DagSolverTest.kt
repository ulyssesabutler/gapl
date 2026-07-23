package graph

import com.uabutler.util.Logger
import com.uabutler.netlistir.transformer.util.retiming.solver.DagSolver
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.findMinimumClockPeriod
import graph.TestUtil.createGraph
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DagSolver only accepts genuinely acyclic circuits - unlike every other solver here, a
 * cycle with a register protecting it (the normal shape of a real stateful/feedback circuit)
 * is out of scope entirely, so these fixtures are deliberately DAG-only (chain/branches), not
 * shared with ClockPeriodMinimizationTest/SccSolverTest's cyclic ones.
 */
class DagSolverTest {

    fun Graph.solveDag(targetClockPeriod: Int?): Graph? {
        val solver = DagSolver(MonolithicRetimingProblem(this))
        return solver.solveOrNull(targetClockPeriod)?.graph
    }

    fun Graph.minimizeClockPeriod(): Graph {
        val problem = MonolithicRetimingProblem(this)
        val solver = DagSolver(problem)
        val minimumClockPeriod = findMinimumClockPeriod(solver, problem)
        return solver.solveOrNull(minimumClockPeriod)!!.graph
    }

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `retime chain`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 1),
                EdgeSketch("b", "c", 0),
                EdgeSketch("c", "d", 0),
            )
        )

        val retimedGraph = graph.minimizeClockPeriod()

        // We should have a register between every pair of nodes
        retimedGraph.edges.forEach { edge ->
            assertEquals(1, edge.weight, "Edge ${edge.value} should have a register, has ${edge.weight}")
        }
    }

    @Test
    fun `retime branches`() {
        val shortBranchNodes = listOf("branchStart") + List(3) { "a$it" } + listOf("branchEnd")
        val longBranchNodes = listOf("branchStart") + List(10) { "b$it" } + listOf("branchEnd")

        val shortBranchEdges = shortBranchNodes.zipWithNext().map { EdgeSketch(it.first, it.second, 0) }
        val longBranchEdges = longBranchNodes.zipWithNext().map { EdgeSketch(it.first, it.second, 0) }

        val edgeList = buildList {
            add(EdgeSketch("circuitStart", "branchStart", 0))
            add(EdgeSketch("branchEnd", "circuitEnd", 0))
            addAll(shortBranchEdges)
            addAll(longBranchEdges)
        }

        val graph = createGraph(
            name = "branches",
            edgeList = edgeList,
        )

        val retimedGraph = graph.minimizeClockPeriod()
        assertTrue(retimedGraph.computeClockPeriod() <= graph.computeClockPeriod(), "Retiming should not increase clock period")
    }

    @Test
    fun `null target passes the graph through unmodified`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 1),
                EdgeSketch("b", "c", 0),
            )
        )

        val result = graph.solveDag(null)
        assertEquals(graph.edges.map { it.weight }, result!!.edges.map { it.weight })
    }

    @Test
    fun `infeasible target period returns null`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 0),
                EdgeSketch("b", "c", 0),
            ),
            weightOverride = mapOf("a" to 5, "b" to 5, "c" to 5),
        )

        // A single node's own delay already exceeds this period - dagRetime treats that as a
        // hard precondition (it throws), but DagSolver translates it into ordinary infeasibility.
        assertNull(graph.solveDag(1))
    }

    @Test
    fun `a graph with a real register-protected cycle is out of scope, not just harder`() {
        // Same fixture ClockPeriodMinimizationTest/SccSolverTest use for "retime simple cycle" -
        // well-formed (the cycle has a register), but not a DAG, which is DagSolver's actual
        // domain restriction, distinct from any particular target period being infeasible.
        val graph = createGraph(
            name = "cycle",
            edgeList = listOf(
                EdgeSketch("a", "b", 0),
                EdgeSketch("b", "c", 0),
                EdgeSketch("c", "d", 1),
                EdgeSketch("d", "a", 0),
            )
        )

        assertFailsWith<Exception> { graph.solveDag(10) }
    }

}
