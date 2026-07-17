package graph

import com.uabutler.util.Logger
import graph.HierarchicalTestUtil.createHierarchicalGraph
import graph.HierarchicalTestUtil.getCorrespondingEdge
import graph.HierarchicalTestUtil.solve
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewHierarchicalMinimalRegisterSolverTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** 4-node cycle with [registerWeight] registers on d→a, plus vIn/vOut boundary virtual nodes. */
    private fun fourNodeCycleGraph(
        name: String,
        registerWeight: Int,
        nodeWeight: Int = 1,
    ): Pair<HGraph, List<HierarchicalEdgeSketch>> {
        val cycleEdges = listOf(
            HierarchicalEdgeSketch("a", "b", 0),
            HierarchicalEdgeSketch("b", "c", 0),
            HierarchicalEdgeSketch("c", "d", 0),
            HierarchicalEdgeSketch("d", "a", registerWeight),
        )
        val boundaryEdges = listOf(
            HierarchicalEdgeSketch("vIn", "a", 0),
            HierarchicalEdgeSketch("d", "vOut", 0),
        )
        val graph = createHierarchicalGraph(
            name = name,
            edgeList = cycleEdges + boundaryEdges,
            leafWeights = mapOf("a" to nodeWeight, "b" to nodeWeight, "c" to nodeWeight, "d" to nodeWeight),
            virtualNodes = setOf("vIn", "vOut"),
        )
        return graph to cycleEdges
    }

    // -----------------------------------------------------------------------
    // Flat (no children) — unretimed timing properties
    // -----------------------------------------------------------------------

    @Test
    fun `unretimed timing properties — four-node cycle with 2 registers`() {
        val (graph, _) = fourNodeCycleGraph("cycle-unretimed", registerWeight = 2)
        val result = solve(graph, targetClockPeriod = 4)

        assertNotNull(result, "Solver should find a solution for targetClockPeriod=4")

        val props = result.unretimedProperties
        // The direct path vIn→a→b→c→d→vOut crosses 0 registers (d→a is not on this path)
        assertEquals(0, props.registerDelay, "Unretimed register delay should be 0")
        // All 4 nodes on the direct path are combinational → delay 4
        assertEquals(4, props.combinationalDelay, "Unretimed combinational delay should be 4")
        assertEquals(4, props.clockPeriod, "Unretimed clock period should be 4")
        assertEquals(2, props.registerCount, "Total register count should be 2")
    }

    // -----------------------------------------------------------------------
    // Flat (no children) — retiming
    // -----------------------------------------------------------------------

    @Test
    fun `flat hierarchical graph — cycle registers are preserved after retiming`() {
        val (graph, cycleEdges) = fourNodeCycleGraph("cycle-retime", registerWeight = 2)
        val result = solve(graph, targetClockPeriod = 2)

        assertNotNull(result, "Solver should find a solution for targetClockPeriod=2")

        val retimedCycleWeight = cycleEdges.sumOf { getCorrespondingEdge(result.retimedGraph.edges, it).weight }
        assertEquals(2, retimedCycleWeight, "Cycle register count must be preserved after retiming")
    }

    @Test
    fun `flat hierarchical graph — clock period constraint is satisfied`() {
        val (graph, _) = fourNodeCycleGraph("cycle-period", registerWeight = 2)
        val result = solve(graph, targetClockPeriod = 2)

        assertNotNull(result)
        assertTrue(result.retimedProperties.clockPeriod <= 2, "Retimed clock period should be ≤ 2")
    }

    @Test
    fun `flat hierarchical graph — infeasible target returns null`() {
        // 3-node cycle with 1 register: minimum achievable clock period is 3
        val edges = listOf(
            HierarchicalEdgeSketch("vIn", "a", 0),
            HierarchicalEdgeSketch("a", "b", 0),
            HierarchicalEdgeSketch("b", "c", 0),
            HierarchicalEdgeSketch("c", "a", 1),
            HierarchicalEdgeSketch("b", "vOut", 0),
        )
        val graph = createHierarchicalGraph(
            name = "3-node-cycle",
            edgeList = edges,
            virtualNodes = setOf("vIn", "vOut"),
        )

        val result = solve(graph, targetClockPeriod = 1)
        assertNull(result, "Solver should return null when target period is unachievable")
    }

    // -----------------------------------------------------------------------
    // Two-level hierarchy
    // -----------------------------------------------------------------------

    @Test
    fun `two-level hierarchy — child and parent are both solved`() {
        val (childGraph, _) = fourNodeCycleGraph("child", registerWeight = 2)

        val parentEdges = listOf(
            HierarchicalEdgeSketch("parentIn", "x", 0),
            HierarchicalEdgeSketch("x", "child", 0),
            HierarchicalEdgeSketch("child", "y", 0),
            HierarchicalEdgeSketch("y", "parentOut", 0),
        )
        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = parentEdges,
            childGraphs = mapOf("child" to childGraph),
            virtualNodes = setOf("parentIn", "parentOut"),
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 3)

        assertNotNull(results[childGraph], "Child graph should be solved")
        assertNotNull(results[parentGraph], "Parent graph should be solved")
    }

    @Test
    fun `two-level hierarchy — child clock period constraint is satisfied`() {
        val (childGraph, _) = fourNodeCycleGraph("child", registerWeight = 2)

        val parentEdges = listOf(
            HierarchicalEdgeSketch("parentIn", "x", 0),
            HierarchicalEdgeSketch("x", "child", 0),
            HierarchicalEdgeSketch("child", "y", 0),
            HierarchicalEdgeSketch("y", "parentOut", 0),
        )
        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = parentEdges,
            childGraphs = mapOf("child" to childGraph),
            virtualNodes = setOf("parentIn", "parentOut"),
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 3)

        val childResult = results[childGraph]!!
        assertTrue(childResult.retimedProperties.clockPeriod <= 3)
    }

    @Test
    fun `two-level hierarchy — child registers are preserved after retiming`() {
        val (childGraph, childCycleEdges) = fourNodeCycleGraph("child", registerWeight = 2)

        val parentEdges = listOf(
            HierarchicalEdgeSketch("parentIn", "x", 0),
            HierarchicalEdgeSketch("x", "child", 0),
            HierarchicalEdgeSketch("child", "y", 0),
            HierarchicalEdgeSketch("y", "parentOut", 0),
        )
        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = parentEdges,
            childGraphs = mapOf("child" to childGraph),
            virtualNodes = setOf("parentIn", "parentOut"),
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 3)

        val childResult = results[childGraph]!!
        val retimedCycleWeight = childCycleEdges.sumOf {
            getCorrespondingEdge(childResult.retimedGraph.edges, it).weight
        }
        assertEquals(2, retimedCycleWeight, "Child cycle register count must be preserved")
    }
}
