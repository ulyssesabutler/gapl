package graph

import com.uabutler.util.Logger
import graph.HierarchicalTestUtil.createHierarchicalGraph
import graph.TestUtil.createGraph
import graph.HierarchicalTestUtil.solve
import graph.print
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NewHierarchicalMinimalRegisterSolverTest {

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
            virtualNodes = setOf("child-in", "child-out"),
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
            virtualNodes = setOf("in", "out"),
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
            virtualNodes = setOf("child-in", "child-out"),
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
            virtualNodes = setOf("in", "out"),
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 1)
        val flattenedGraph = results[parentGraph]!!.retimedGraph.flatten()

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
            virtualNodes = setOf("child-in", "child-out"),
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
            virtualNodes = setOf("in", "out"),
        )

        val results = solve(listOf(childGraph, parentGraph), targetClockPeriod = 1)
        val flattenedGraph = results[parentGraph]!!.retimedGraph.flatten()

        flattenedGraph.print()

        assertEquals(1, flattenedGraph.computeClockPeriod())
    }

}
