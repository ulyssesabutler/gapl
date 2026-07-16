package graph

import com.uabutler.util.Logger
import com.uabutler.util.graph.util.MinimalRegisterSolver
import graph.TestUtil.createGraph
import graph.TestUtil.getCorrespondingEdge
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterMinimizationTest {

    fun Graph.minimizeRegisterCount(clockPeriod: Int? = null): Graph {
        val solver = MinimalRegisterSolver(this)
        return solver.solveOrNull(clockPeriod)!!
    }

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `if there are no cycles, all registers will be removed`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 1),
                EdgeSketch("b", "c", 2),
                EdgeSketch("c", "d", 3),
            )
        )

        val retimedGraph = graph.minimizeRegisterCount()

        retimedGraph.edges.forEach { edge ->
            assertEquals(0, edge.weight, "Edge ${edge.value} should have no registers, has ${edge.weight}")
        }
    }

    @Test
    fun `if there is a cycle, no registers will be removed`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 1),
                EdgeSketch("b", "c", 1),
                EdgeSketch("c", "d", 1),
                EdgeSketch("d", "a", 1),
            )
        )

        val retimedGraph = graph.minimizeRegisterCount()

        val registerCount = retimedGraph.edges.sumOf { it.weight }
        assertEquals(4, registerCount, "Graph should have 4 registers, has $registerCount")
    }

    @Test
    fun `registers will be moved across nodes to lower fan-out`() {
        val largeFanOut = listOf(
            EdgeSketch("a", "b", 1),
            EdgeSketch("a", "c", 1),
            EdgeSketch("b", "d", 1),
            EdgeSketch("c", "d", 1),
        )

        val smallFanOut = listOf(
            EdgeSketch("d", "a", 0),
        )

        val graph = createGraph(
            name = "chain",
            edgeList = largeFanOut + smallFanOut
        )

        val retimedGraphEdges = graph.minimizeRegisterCount().edges

        largeFanOut
            .map { getCorrespondingEdge(retimedGraphEdges, it) }
            .forEach { edge ->
                assertEquals(0, edge.weight, "Edge ${edge.value} should have no registers, has ${edge.weight}")
            }

        smallFanOut
            .map { getCorrespondingEdge(retimedGraphEdges, it) }
            .forEach { edge ->
                assertEquals(2, edge.weight, "Edge ${edge.value} should have 1 register, has ${edge.weight}")
            }
    }

    @Test
    fun `retiming will respect time period constraint`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                EdgeSketch("a", "b", 0),
                EdgeSketch("b", "c", 0),
                EdgeSketch("c", "d", 1),
                EdgeSketch("d", "e", 0),
                EdgeSketch("e", "f", 0),
            )
        )

        val retimedGraph = graph.minimizeRegisterCount(3)

        retimedGraph.edges.sumOf { it.weight }.let { assertEquals(1, it, "Graph should have 1 registers, has $it") }
    }

}