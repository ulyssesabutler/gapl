package graph

import com.uabutler.util.Logger
import graph.HierarchicalTestUtil.createHierarchicalGraph
import graph.HierarchicalTestUtil.getCorrespondingEdge
import graph.HierarchicalTestUtil.solve
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HierarchicalMinimalRegisterSolverTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `flattening works`() {
        val childGraph = createHierarchicalGraph(
            name = "child",
            edgeList = listOf(
                Edge("child-in", "child-a", 0),
                Edge("child-a", "child-b", 0),
                Edge("child-b", "child-out", 0),
            ),
            leafWeights = mapOf("child-a" to 1, "child-b" to 1),
            rootAttachment = "child-in",
            leafAttachment = "child-out",
        )

        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = listOf(
                Edge("in", "a", 0),
                Edge("a", "child", 0),
                Edge("child", "b", 0),
                Edge("b", "out", 0),
            ),
            leafWeights = mapOf("a" to 1, "b" to 1),
            childGraphs = mapOf("child" to childGraph),
            rootAttachment = "in",
            leafAttachment = "out",
        )

        val flattenedGraph = parentGraph.flatten()

        val expectedNodes = mapOf(
            "in" to 0,
            "a" to 1,
            "child-in" to 0,
            "child-a" to 1,
            "child-b" to 1,
            "child-out" to 0,
            "b" to 1,
            "out" to 0,
        )

        val actualNodes = flattenedGraph.nodes.associate { it.value to it.weight }

        val expectedEdges = mapOf(
            ("in" to "a") to 0,
            ("a" to "child-in") to 0,
            ("child-in" to "child-a") to 0,
            ("child-a" to "child-b") to 0,
            ("child-b" to "child-out") to 0,
            ("child-out" to "b") to 0,
            ("b" to "out") to 0,
        )

        val actualEdges = flattenedGraph.edges.associate { (it.source.value to it.sink.value) to it.weight }

        assertEquals(expectedNodes, actualNodes)
        assertEquals(expectedEdges, actualEdges)

        assertEquals(4, flattenedGraph.computeClockPeriod())
    }

    @Test
    fun `hierarchical retiming adds registers to child and parent`() {
        val childGraph = createHierarchicalGraph(
            name = "child",
            edgeList = listOf(
                Edge("child-in", "child-a", 0),
                Edge("child-a", "child-b", 0),
                Edge("child-b", "child-out", 0),
            ),
            leafWeights = mapOf("child-a" to 1, "child-b" to 1),
            rootAttachment = "child-in",
            leafAttachment = "child-out",
        )

        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = listOf(
                Edge("in", "a", 0),
                Edge("a", "child", 0),
                Edge("child", "b", 0),
                Edge("b", "out", 0),
            ),
            leafWeights = mapOf("a" to 1, "b" to 1),
            childGraphs = mapOf("child" to childGraph),
            rootAttachment = "in",
            leafAttachment = "out",
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 1)
        val flattenedGraph = results!!.getValue(parentGraph).flatten()

        assertEquals(1, flattenedGraph.computeClockPeriod())
    }

    @Test
    fun `hierarchical retiming works when child does not need retimed`() {
        val childGraph = createHierarchicalGraph(
            name = "child",
            edgeList = listOf(
                Edge("child-in", "child-a", 0),
                Edge("child-a", "child-out", 0),
            ),
            leafWeights = mapOf("child-a" to 1),
            rootAttachment = "child-in",
            leafAttachment = "child-out",
        )

        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = listOf(
                Edge("in", "a", 0),
                Edge("a", "child", 0),
                Edge("child", "b", 0),
                Edge("b", "out", 0),
            ),
            leafWeights = mapOf("a" to 1, "b" to 1),
            childGraphs = mapOf("child" to childGraph),
            rootAttachment = "in",
            leafAttachment = "out",
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 1)
        val flattenedGraph = results!!.getValue(parentGraph).flatten()

        assertEquals(1, flattenedGraph.computeClockPeriod())
    }

    /**
     * A child's component retiming difference must come from its own retiming labels
     * (r(super-out) - r(super-in)), not from the change in the fewest-register path to *any*
     * graph-theoretic leaf.
     *
     * `child-dead` is an internal value that is never consumed, so it is a second leaf of the child
     * alongside `child-out`. A retiming shifts paths to different sinks by different amounts, so a
     * minimum taken across both sinks does not shift by the component retiming difference - it used
     * to come out as 0 here instead of 1. The parent then pinned r(c_o) - r(c_i) = 0, dropped the
     * register that balances its bypass path against the extra cycle the child now takes, and
     * emitted a circuit whose two paths reconverge one cycle apart.
     */
    private fun assertChildLatencyIsBalancedInParent(childHasUnusedInternalValue: Boolean) {
        val childGraph = createHierarchicalGraph(
            name = "child",
            edgeList = buildList {
                add(Edge("child-in", "child-a", 0))
                add(Edge("child-a", "child-b", 0))
                add(Edge("child-b", "child-out", 0))
                if (childHasUnusedInternalValue) add(Edge("child-in", "child-dead", 0))
            },
            leafWeights = mapOf("child-a" to 2, "child-b" to 2, "child-dead" to 1),
            rootAttachment = "child-in",
            leafAttachment = "child-out",
        )

        val bypassEdge = Edge("p", "out", 0)
        val parentGraph = createHierarchicalGraph(
            name = "parent",
            edgeList = listOf(
                Edge("in", "child", 0),
                Edge("child", "out", 0),
                Edge("in", "p", 0),
                bypassEdge,
            ),
            leafWeights = mapOf("p" to 1),
            childGraphs = mapOf("child" to childGraph),
            rootAttachment = "in",
            leafAttachment = "out",
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 2)
        assertNotNull(results, "target period 2 should be achievable")

        // child-a and child-b are weight 2 each, so the child must break that path with a register,
        // taking it from 0 cycles of latency to 1.
        val retimedChild = results.getValue(childGraph)
        assertEquals(
            1,
            retimedChild.edges.sumOf { it.weight },
            "child should be retimed to one register regardless of the unused internal value",
        )

        // The parent's bypass must therefore pick up a matching cycle of latency.
        val retimedParent = results.getValue(parentGraph)
        assertEquals(
            1,
            getCorrespondingEdge(retimedParent.edges, bypassEdge).weight,
            "parent bypass must be balanced against the cycle of latency the child added",
        )
    }

    @Test
    fun `parent balances a child's added latency`() {
        assertChildLatencyIsBalancedInParent(childHasUnusedInternalValue = false)
    }

    @Test
    fun `parent balances a child's added latency when the child has an unused internal value`() {
        assertChildLatencyIsBalancedInParent(childHasUnusedInternalValue = true)
    }

}
