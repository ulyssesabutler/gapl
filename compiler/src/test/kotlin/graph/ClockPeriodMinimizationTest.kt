package graph

import graph.TestUtil.createGraph
import graph.TestUtil.getCorrespondingEdge
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClockPeriodMinimizationTest {

    /* TODO:
     *   - We still need tests to ensure uneven weights are handled correctly
     *   - A test with uneven branches is probably not necessary, but wouldn't hurt
     */

    @Test
    fun `retime chain`() {
        val graph = createGraph(
            name = "chain",
            edgeList = listOf(
                Edge("a", "b", 1),
                Edge("b", "c", 0),
                Edge("c", "d", 0),
            )
        )

        val retimedGraph = graph.retimed()

        // We should have a register between every pair of nodes
        retimedGraph.edges.forEach { edge ->
            assertEquals(1, edge.weight, "Edge ${edge.value} should have a register, has ${edge.weight}")
        }
    }

    @Test
    fun `retime simple cycle`() {
        val cycle = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 1),
            Edge("d", "a", 0),
        )

        val start = Edge("start", "a", 0)
        val end = Edge("b", "end", 0)

        val edgeList = buildList {
            add(start)
            addAll(cycle)
            add(end)
        }

        val graph = createGraph(
            name = "cycle",
            edgeList = edgeList,
        )

        val retimedGraphEdges = graph.retimed().edges

        val cycleWeight = cycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(1, cycleWeight, "Cycle weight should be 1, is $cycleWeight")
    }

    @Test
    fun `retime simple cycle and chain`() {
        val cycle = listOf(
            Edge("a1", "a2", 0),
            Edge("a2", "a3", 0),
            Edge("a3", "a4", 1),
            Edge("a4", "a1", 0),
        )

        val start = Edge("start", "a1", 0)

        val chain = listOf(
            Edge("a2", "b1", 0),
            Edge("b1", "b2", 0),
            Edge("b2", "b3", 0),
            Edge("b3", "b4", 0),
            Edge("b4", "b5", 0),
            Edge("b5", "b6", 0),
            Edge("b6", "b7", 0),
            Edge("b7", "b8", 0),
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

        val retimedGraphEdges = graph.retimed().edges

        val cycleWeight = cycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        val chainWeight = chain.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(1, cycleWeight, "Cycle weight should be 1, is $cycleWeight")
        assertTrue(chainWeight >= 2, "Chain weight should be at least 2, is $chainWeight")
    }

    @Test
    fun `retime multiple interconnected cycles`() {
        val firstCycle = listOf(
            Edge("a1", "a2", 0),
            Edge("a2", "a3", 1),
            Edge("a3", "a1", 0),
        )

        val secondCycle = listOf(
            Edge("b1", "b2", 1),
            Edge("b2", "b3", 0),
            Edge("b3", "b1", 0),
        )

        val interconnect = listOf(
            Edge("a2", "b1", 0),
            Edge("b2", "a3", 0),
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

        val retimedGraphEdges = graph.retimed().edges

        val firstCycleWeight = firstCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        val secondCycleWeight = secondCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(1, firstCycleWeight, "First cycle weight should be 1, is $firstCycleWeight")
        assertEquals(1, secondCycleWeight, "Second cycle weight should be 1, is $secondCycleWeight")
    }

    @Test
    fun `retime branches`() {
        val shortBranchNodes = listOf("branchStart") + List(3) { "a$it" } + listOf("branchEnd")
        val longBranchNodes = listOf("branchStart") + List(10) { "b$it" } + listOf("branchEnd")

        val shortBranchEdges = shortBranchNodes.zipWithNext().map { Edge(it.first, it.second, 0) }
        val longBranchEdges = longBranchNodes.zipWithNext().map { Edge(it.first, it.second, 0) }

        val edgeList = buildList {
            add(Edge("circuitStart", "branchStart", 0))
            add(Edge("branchEnd", "circuitEnd", 0))
            addAll(shortBranchEdges)
            addAll(longBranchEdges)
        }

        val graph = createGraph(
            name = "branches",
            edgeList = edgeList,
        )

        val retimedGraphEdges = graph.retimed().edges

        val longBranchWeight = longBranchEdges.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        val shortBranchWeight = shortBranchEdges.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(shortBranchWeight, longBranchWeight, "Short branch weight should be equal to long branch weight, short is $shortBranchWeight, long is $longBranchWeight")
    }

    @Test
    fun `retime multiple registers in cycle`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 2),
            Edge("c", "d", 0),
            Edge("d", "a", 0),
        )

        val graph = createGraph(
            name = "multiple registers in cycle",
            edgeList = edgeList,
        )

        val preRetimingClockPeriod = graph.computeClockPeriod()
        assertEquals(4, preRetimingClockPeriod, "Graph should have period 4, is $preRetimingClockPeriod")

        val retimedGraph = graph.retimed()
        val retimedGraphEdges = retimedGraph.edges

        val cycleWeight = edgeList.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(2, cycleWeight, "Cycle should have maintained weight of 2, is $cycleWeight")

        val aWeight = retimedGraphEdges.first { it.source.value == "a" }.weight
        val bWeight = retimedGraphEdges.first { it.source.value == "b" }.weight
        val cWeight = retimedGraphEdges.first { it.source.value == "c" }.weight
        val dWeight = retimedGraphEdges.first { it.source.value == "d" }.weight

        if (aWeight > 0) {
            assertEquals(1, aWeight, "a should have weight 1, has $aWeight")
            assertEquals(1, cWeight, "c should have weight 1, has $cWeight")
        } else {
            assertEquals(1, bWeight, "b should have weight 1, has $bWeight")
            assertEquals(1, dWeight, "d should have weight 1, has $dWeight")
        }

        val postRetimingClockPeriod = retimedGraph.computeClockPeriod()
        assertEquals(2, postRetimingClockPeriod, "Retimed graph should have period 2, is $postRetimingClockPeriod")
    }

    @Test
    fun `retime large cycle`() {
        val nodes = List(1000) { "node$it" }
        val edges = nodes.zipWithNext().map { Edge(it.first, it.second, 0) } + Edge(nodes.last(), nodes.first(), 500)

        val graph = createGraph(
            name = "multiple registers in cycle",
            edgeList = edges,
        )

        val preRetimingClockPeriod = graph.computeClockPeriod()
        assertEquals(1000, preRetimingClockPeriod, "Graph should have period 1000, is $preRetimingClockPeriod")

        val retimedGraph = graph.retimed()
        val retimedGraphEdges = retimedGraph.edges

        val cycleWeight = edges.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(500, cycleWeight, "Cycle should have maintained weight of 500, is $cycleWeight")

        val pairedEdges = nodes
            .map { retimedGraphEdges.first { edge -> edge.source.value == it } }
            .zipWithNext()

        pairedEdges.forEach { (a, b) -> assertEquals(1, a.weight + b.weight, "Edge weights not evenly distributed, ${a.value}: ${a.weight}, ${b.value}: ${b.weight}") }

        val postRetimingClockPeriod = retimedGraph.computeClockPeriod()
        assertEquals(2, postRetimingClockPeriod, "Retimed graph should have period 2, is $postRetimingClockPeriod")
    }

}