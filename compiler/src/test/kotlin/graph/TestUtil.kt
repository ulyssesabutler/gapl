package graph

import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

typealias Graph = LeisersonCircuitGraph<String, String, String>

data class Edge(
    val source: String,
    val sink: String,
    val weight: Int,
) {
    fun value() = "$source -> $sink"
    override fun toString() = value()
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

object TestUtil {

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

    fun getSublistSums(list: List<Int>): Set<Int> {
        if (list.isEmpty()) return emptySet()

        fun prefixSums(list: List<Int>) = list.runningFold(0) { acc, i -> acc + i }

        val first = list.first()
        val prefixSums = prefixSums(list.drop(1)).toSet()

        return buildSet {
            add(first)
            addAll(prefixSums.map { it + first })
            addAll(getSublistSums(list.drop(1)))
        }
    }

}