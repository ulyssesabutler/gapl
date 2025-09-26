package retiming

import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import org.junit.jupiter.api.Test
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
            Edge("cycle1", "cycle2", 0),
            Edge("cycle2", "cycle3", 0),
            Edge("cycle3", "cycle4", 1),
            Edge("cycle4", "cycle1", 0),
        )

        val start = Edge("start", "cycle1", 0)

        val chain = listOf(
            Edge("cycle2", "chain1", 0),
            Edge("chain1", "chain2", 0),
            Edge("chain2", "chain3", 0),
            Edge("chain3", "chain4", 0),
            Edge("chain4", "chain5", 0),
            Edge("chain5", "chain6", 0),
            Edge("chain6", "chain7", 0),
            Edge("chain7", "chain8", 0),
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

    fun Graph.print() {
        println("Graph: $value")

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