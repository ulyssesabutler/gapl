package retiming

import com.uabutler.util.graph.WeightedGraph
import org.junit.jupiter.api.Test

typealias Graph = WeightedGraph<String, String>

class ClockPeriodMinimizationTest {

    @Test
    fun `retime linear circuit`() {
        val graph = createGraph(
            edgeList = listOf(
                Edge("a", "b", 1),
                Edge("b", "c", 0),
                Edge("c", "d", 0),
            )
        )
    }

    fun createGraph(edgeList: List<Edge>, weightOverride: Map<String, Int> = emptyMap()): Graph {
        val nodes = edgeList
            .flatMap { listOf(it.source, it.sink) }
            .toSet()
            .associateWith { weightOverride[it] ?: 0 }
            .map { (node, weight) -> WeightedGraph.Node(weight, node) }
            .associateBy { it.value }

        val edges = edgeList.map { (source, sink, weight) ->
            WeightedGraph.Edge(
                source = nodes[source]!!,
                sink = nodes[sink]!!,
                value = "$source -> $sink",
                weight = weight,
            )
        }

        return Graph(nodes.values.toList(), edges)
    }

    fun Graph.print() {
        println("Graph:")

        println("  Nodes:")
        nodes.forEach { node ->
            println("    ${node.value}: ${node.weight}")
        }

        println("  Edges:")
        edges.forEach { edge ->
            println("    ${edge.source.value} -> ${edge.sink.value}: ${edge.weight}")
        }
    }

    data class Edge(
        val source: String,
        val sink: String,
        val weight: Int,
    )

}