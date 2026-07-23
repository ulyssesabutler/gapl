package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

class Retiming<G, N, E>(
    val graph: LeisersonCircuitGraph<G, N, E>,
    private val graphFactory: (Collection<WeightedGraph.Node<N>>, Collection<WeightedGraph.Edge<N, E>>) -> LeisersonCircuitGraph<G, N, E>
) {

    private val nodeLag = graph.nodes.associateWith { 0 }.toMutableMap()

    fun setNodeLag(node: WeightedGraph.Node<N>, lag: Int) = nodeLag.put(node, lag)

    fun getNodeLag(node: WeightedGraph.Node<N>) = try {
        nodeLag[node]!!
    } catch (e: NullPointerException) {
        nodeLag.keys.forEach { Logger.debug { "$it: ${nodeLag[it]}" } }
        throw Exception("Node $node is not in the graph", e)
    }

    fun getEdgeRegisterCount(edge: WeightedGraph.Edge<N, E>): Int = edge.weight + getNodeLag(edge.sink) - getNodeLag(edge.source)

    fun increaseNodeLag(node: WeightedGraph.Node<N>, increase: Int = 1) = setNodeLag(node, getNodeLag(node) + increase)

    fun generateNewCircuit(): LeisersonCircuitGraph<G, N, E> {
        return try {
            graphFactory(
                graph.nodes,
                graph.edges.map { edge -> edge.copy(weight = getEdgeRegisterCount(edge)) },
            )
        } catch (e: IllegalArgumentException) {
            throw Exception("Failed to generate graph: Illegal retiming", e)
        }
    }

}