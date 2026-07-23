package graph

import com.uabutler.util.Logger
import com.uabutler.netlistir.transformer.util.retiming.solver.SccSolver
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.findMinimumClockPeriod
import graph.TestUtil.createGraph
import graph.TestUtil.getCorrespondingEdge
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SccSolverTest {

    fun Graph.solveScc(targetClockPeriod: Int?): Graph? {
        val solver = SccSolver(MonolithicRetimingProblem(this))
        return solver.solveOrNull(targetClockPeriod)?.graph
    }

    fun Graph.minimizeClockPeriod(): Graph {
        val problem = MonolithicRetimingProblem(this)
        val solver = SccSolver(problem)
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
    fun `retime simple cycle`() {
        val cycle = listOf(
            EdgeSketch("a", "b", 0),
            EdgeSketch("b", "c", 0),
            EdgeSketch("c", "d", 1),
            EdgeSketch("d", "a", 0),
        )

        val start = EdgeSketch("start", "a", 0)
        val end = EdgeSketch("b", "end", 0)

        val edgeList = buildList {
            add(start)
            addAll(cycle)
            add(end)
        }

        val graph = createGraph(
            name = "cycle",
            edgeList = edgeList,
        )

        val retimedGraph = graph.minimizeClockPeriod()
        val retimedGraphEdges = retimedGraph.edges

        // Any legal retiming conserves a cycle's total register count, not just a minimal one.
        val cycleWeight = cycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        assertEquals(1, cycleWeight, "Cycle weight should be 1, is $cycleWeight")

        assertTrue(retimedGraph.computeClockPeriod() <= graph.computeClockPeriod(), "Retiming should not increase clock period")
    }

    @Test
    fun `retime simple cycle and chain`() {
        val cycle = listOf(
            EdgeSketch("a1", "a2", 0),
            EdgeSketch("a2", "a3", 0),
            EdgeSketch("a3", "a4", 1),
            EdgeSketch("a4", "a1", 0),
        )

        val start = EdgeSketch("start", "a1", 0)

        val chain = listOf(
            EdgeSketch("a2", "b1", 0),
            EdgeSketch("b1", "b2", 0),
            EdgeSketch("b2", "b3", 0),
            EdgeSketch("b3", "b4", 0),
            EdgeSketch("b4", "b5", 0),
            EdgeSketch("b5", "b6", 0),
            EdgeSketch("b6", "b7", 0),
            EdgeSketch("b7", "b8", 0),
        )

        val edgeList = buildList {
            add(start)
            addAll(cycle)
            addAll(chain)
        }

        val graph = createGraph(
            name = "cycle and chain",
            edgeList = edgeList,
        )

        val retimedGraphEdges = graph.minimizeClockPeriod().edges

        val cycleWeight = cycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        assertEquals(1, cycleWeight, "Cycle weight should be 1, is $cycleWeight")
    }

    @Test
    fun `retime multiple interconnected cycles`() {
        val firstCycle = listOf(
            EdgeSketch("a1", "a2", 0),
            EdgeSketch("a2", "a3", 1),
            EdgeSketch("a3", "a1", 0),
        )

        val secondCycle = listOf(
            EdgeSketch("b1", "b2", 1),
            EdgeSketch("b2", "b3", 0),
            EdgeSketch("b3", "b1", 0),
        )

        val interconnect = listOf(
            EdgeSketch("a2", "b1", 0),
            EdgeSketch("b2", "a3", 0),
        )

        val edgeList = buildList {
            addAll(firstCycle)
            addAll(secondCycle)
            addAll(interconnect)
        }

        val graph = createGraph(
            name = "multiple cycles",
            edgeList = edgeList,
        )

        val retimedGraphEdges = graph.minimizeClockPeriod().edges

        val firstCycleWeight = firstCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        val secondCycleWeight = secondCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(1, firstCycleWeight, "First cycle weight should be 1, is $firstCycleWeight")
        assertEquals(1, secondCycleWeight, "Second cycle weight should be 1, is $secondCycleWeight")
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
    fun `retime multiple registers in cycle`() {
        val edgeList = listOf(
            EdgeSketch("a", "b", 0),
            EdgeSketch("b", "c", 2),
            EdgeSketch("c", "d", 0),
            EdgeSketch("d", "a", 0),
        )

        val graph = createGraph(
            name = "multiple registers in cycle",
            edgeList = edgeList,
        )

        val retimedGraph = graph.minimizeClockPeriod()
        val retimedGraphEdges = retimedGraph.edges

        val cycleWeight = edgeList.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        assertEquals(2, cycleWeight, "Cycle should have maintained weight of 2, is $cycleWeight")

        val postRetimingClockPeriod = retimedGraph.computeClockPeriod()
        assertTrue(postRetimingClockPeriod <= graph.computeClockPeriod(), "Retiming should not increase clock period")
    }

    @Test
    fun `retime large cycle`() {
        val nodes = List(1000) { "node$it" }
        val edges = nodes.zipWithNext().map { EdgeSketch(it.first, it.second, 0) } + EdgeSketch(nodes.last(), nodes.first(), 500)

        val graph = createGraph(
            name = "large cycle",
            edgeList = edges,
        )

        val retimedGraph = graph.minimizeClockPeriod()
        val retimedGraphEdges = retimedGraph.edges

        val cycleWeight = edges.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        assertEquals(500, cycleWeight, "Cycle should have maintained weight of 500, is $cycleWeight")
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

        val result = graph.solveScc(null)
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

        // A single node's own delay already exceeds this period, so no retiming can help.
        assertNull(graph.solveScc(1))
    }

}
