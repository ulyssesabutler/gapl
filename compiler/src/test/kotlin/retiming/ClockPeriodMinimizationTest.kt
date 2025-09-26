package retiming

import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import org.junit.jupiter.api.Test
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue

typealias Graph = LeisersonCircuitGraph<String, String, String>

class ClockPeriodMinimizationTest {

    @Test
    fun `retime chain`() {
        val graph = createGraph(
            name = "Chain",
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
            name = "Cycle",
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
            name = "Cycle and Chain",
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
            name = "Multiple Cycles",
            edgeList = edgeList,
        )

        val retimedGraphEdges = graph.retimed().edges

        val firstCycleWeight = firstCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }
        val secondCycleWeight = secondCycle.map { getCorrespondingEdge(retimedGraphEdges, it) }.sumOf { it.weight }

        assertEquals(1, firstCycleWeight, "First cycle weight should be 1, is $firstCycleWeight")
        assertEquals(1, secondCycleWeight, "Second cycle weight should be 1, is $secondCycleWeight")
    }

    fun createGraph(name: String, edgeList: List<Edge>, weightOverride: Map<String, Int> = emptyMap()): Graph {
        val nodes = edgeList
            .flatMap { listOf(it.source, it.sink) }
            .toSet()
            .associateWith { weightOverride[it] ?: 1 }
            .map { (node, weight) -> WeightedGraph.Node(weight, node) }
            .associateBy { it.value }

        val edges = edgeList.map {
            WeightedGraph.Edge(
                source = nodes[it.source]!!,
                sink = nodes[it.sink]!!,
                value = it.value(),
                weight = it.weight,
            )
        }

        return Graph(
            value = name,
            nodes = nodes.values.toList(),
            edges = edges,
        )
    }

    fun getCorrespondingEdge(edgeList: Collection<WeightedGraph.Edge<String, String>>, edge: Edge): WeightedGraph.Edge<String, String> {
        return edgeList.first { it.value == edge.value() }
    }

    fun WeightedGraph<String, String>.print() {
        println("Graph")

        println("  Nodes:")
        nodes.forEach { node ->
            println("    ${node.value}: ${node.weight}")
        }

        println("  Edges:")
        edges.forEach { edge ->
            println("    ${edge.value}: ${edge.weight}")
        }
    }

    data class Edge(
        val source: String,
        val sink: String,
        val weight: Int,
    ) {
        fun value() = "$source -> $sink"
        override fun toString() = value()
    }

}