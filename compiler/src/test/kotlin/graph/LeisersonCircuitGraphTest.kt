package graph

import graph.TestUtil.createGraph
import graph.TestUtil.getSublistSums
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class LeisersonCircuitGraphTest {

    @Test
    fun `create graph with cycle`() {
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

        val graph = createGraph("cycle", edgeList)

        assertEquals(6, graph.nodes.size, "Graph should have 6 nodes, ${graph.nodes.size} found")
        assertEquals(6, graph.edges.size, "Graph should have 6 edges, ${graph.edges.size} found")
    }

    @Test
    fun `create graph with zero-weight cycle fails`() {
        val cycle = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 0),
            Edge("d", "a", 0),
        )

        val start = Edge("start", "a", 0)
        val end = Edge("b", "end", 0)

        val edgeList = buildList {
            add(start)
            addAll(cycle)
            add(end)
        }

        assertFails { createGraph("zero-weight cycle", edgeList) }
    }

    @Test
    fun `create graph with negative-weight edge fails`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", -1),
            Edge("c", "d", 0),
        )

        assertFails { createGraph("negative-weight edge", edgeList) }
    }

    @Test
    fun `create graph with negative-weight node fails`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 0),
        )

        val nodeWeights = mapOf("b" to -1)

        assertFails { createGraph("negative-weight node", edgeList, nodeWeights) }
    }

    @Test
    fun `computeCombinationalDelays chain with a register in the middle`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 1), // register breaks the combinational chain
            Edge("c", "d", 0),
        )

        val graph = createGraph("chain-with-mid-reg", edgeList)

        val delays = graph.computeCombinationalDelays()
        val nodeByName = graph.nodes.associateBy { it.value }
        fun delayOf(name: String) = delays.getValue(nodeByName.getValue(name))

        assertEquals(1, delayOf("a"), "a should have delay 1, has ${delayOf("a")}")
        assertEquals(2, delayOf("b"), "b should have delay 2 (1 from a + 1 at b), has ${delayOf("b")}")

        assertEquals(1, delayOf("c"), "c should have delay 1, has ${delayOf("c")}")
        assertEquals(2, delayOf("d"), "d should have delay 2 (1 from c + 1 at d), has ${delayOf("d")}")
    }

    @Test
    fun `computeCombinationalDelays respects non-uniform node weights`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 1),
            Edge("c", "d", 0),
        )
        val weights = mapOf("b" to 3, "c" to 2)

        val graph = createGraph("chain-with-mid-reg-nonuniform", edgeList, weights)

        val delays = graph.computeCombinationalDelays()
        val nodeByName = graph.nodes.associateBy { it.value }
        fun delayOf(name: String) = delays.getValue(nodeByName.getValue(name))

        assertEquals(1, delayOf("a"), "a should have delay 1, has ${delayOf("a")}")
        assertEquals(4, delayOf("b"), "b should have delay 4 (1 from a + 3 from b), has ${delayOf("b")}")

        assertEquals(2, delayOf("c"), "c should have delay 2, has ${delayOf("c")}")
        assertEquals(3, delayOf("d"), "d should have delay 3 (2 from c + 1 at d), has ${delayOf("d")}")
    }

    @Test
    fun `computeCombinationalDelays fan-in takes max incoming delay`() {
        val edgeList = listOf(
            Edge("a", "c", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 0),
        )
        val weights = mapOf(
            "b" to 4,  // heavier, should dominate the fan-in
            "c" to 2,
        )

        val graph = createGraph("fanin-zero-weights", edgeList, weights)

        val delays = graph.computeCombinationalDelays()
        val nodeByName = graph.nodes.associateBy { it.value }
        fun delayOf(name: String) = delays.getValue(nodeByName.getValue(name))

        assertEquals(1, delayOf("a"), "a should have delay 1, has ${delayOf("a")}")
        assertEquals(4, delayOf("b"), "b should have delay 4, has ${delayOf("b")}")

        assertEquals(6, delayOf("c"), "c should have delay 6 (2 from c + 4 from b, not 1 from a), has ${delayOf("c")}")

        assertEquals(7, delayOf("d"), "d should have delay 7 (1 from d + 6 from c), has ${delayOf("d")}")
    }

    @Test
    fun `computeCombinationalDelays ignores registered incoming edges at fan-in`() {
        val edgeList = listOf(
            Edge("a", "c", 1), // register breaks combinational path from a
            Edge("b", "c", 0),
            Edge("c", "d", 0),
        )
        val weights = mapOf(
            "a" to 10, // big, but should be ignored at c due to register
            "b" to 3,
            "c" to 2,
            "d" to 1,
        )

        val graph = createGraph("fanin-mixed-weights", edgeList, weights)

        val delays = graph.computeCombinationalDelays()
        val nodeByName = graph.nodes.associateBy { it.value }
        fun delayOf(name: String) = delays.getValue(nodeByName.getValue(name))

        assertEquals(10, delayOf("a"), "a should have delay 10, has ${delayOf("a")}")
        assertEquals(3, delayOf("b"), "b should have delay 3, has ${delayOf("b")}")

        assertEquals(5, delayOf("c"), "c should have delay 5 (2 from c + 3 from b, not 10 from a), has ${delayOf("c")}")

        assertEquals(6, delayOf("d"), "d should have delay 6 (1 from d + 5 from c), has ${delayOf("d")}")
    }

    @Test
    fun `possible clock periods for simple chain are contiguous sums`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 0),
        )

        val graph = createGraph("chain-all-comb", edgeList)

        val periods = graph.computePossibleClockPeriods().toSet()
        assertEquals(setOf(1, 2, 3, 4), periods, "For a 4-node unit-weight chain, candidates should be {1,2,3,4}")
    }

    @Test
    fun `possible clock periods with non-uniform weights and a register`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c", 0),
            Edge("c", "d", 0),
        )
        val weights = mapOf(
            "a" to 1,
            "b" to 2,
            "c" to 4,
            "d" to 8,
        )

        val graph = createGraph("chain-nonuniform", edgeList, weights)

        val expected = getSublistSums(weights.values.toList())

        val periods = graph.computePossibleClockPeriods().toSet()
        assertEquals(expected, periods, "Candidate clock periods should match sums along minimal-register paths")
    }

    @Test
    fun `possible clock periods on diamond keep only max-sum path for equal-register ties`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c1", 0),
            Edge("b", "c2", 0),
            Edge("c1", "d", 0),
            Edge("c2", "d", 0),
            Edge("d", "e", 0),
        )
        val weights = mapOf(
            "c1" to 2,
            "c2" to 10,
        )

        val graph = createGraph("diamond-branching", edgeList, weights)

        fun weight(node: String) = graph.nodes.first { it.value == node }.weight

        val pathC1Before = listOf(weight("a"), weight("b"), weight("c1"))
        val pathC1After = listOf(weight("c1"), weight("d"), weight("e"))
        val pathC2 = listOf(weight("a"), weight("b"), weight("c2"), weight("d"), weight("e"))

        val expected = getSublistSums(pathC1Before) union getSublistSums(pathC1After) union getSublistSums(pathC2)

        val periods = graph.computePossibleClockPeriods().toSet()

        assertEquals(expected, periods, "With equal-register ties, periods should include only the max-sum path values")
    }

    @Test
    fun `possible clock periods on diamond keeps smaller register path`() {
        val edgeList = listOf(
            Edge("a", "b", 0),
            Edge("b", "c1", 0),
            Edge("b", "c2", 0),
            Edge("c1", "d", 0),
            Edge("c2", "d", 1), // Add register
            Edge("d", "e", 0),
        )
        val weights = mapOf(
            "c1" to 2,
            "c2" to 10,
        )

        val graph = createGraph("diamond-branching", edgeList, weights)

        fun weight(node: String) = graph.nodes.first { it.value == node }.weight

        val pathC2Before = listOf(weight("a"), weight("b"), weight("c2"))
        val pathC2After = listOf(weight("c2"), weight("d"), weight("e"))
        val pathC1 = listOf(weight("a"), weight("b"), weight("c1"), weight("d"), weight("e"))

        val expected = getSublistSums(pathC2Before) union getSublistSums(pathC2After) union getSublistSums(pathC1)

        val periods = graph.computePossibleClockPeriods().toSet()

        assertEquals(expected, periods, "With equal-register ties, periods should include only the max-sum path values")
    }

}