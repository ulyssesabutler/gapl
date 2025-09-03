package com.uabutler.netlistir.transformer.retiming.graph

abstract class AdjacencyList {
    abstract val nodes: List<Node>
    abstract val edges: List<Edge>
    abstract val adjacencyList: Map<Node, List<Edge>>

    class Edge(
        val weight: Int,
        val source: Node,
        val sink: Node,

        val underlyingConnection: WeightedModule.WeightedConnection,
    )

    class Node(
        val weight: Int,

        val underlyingNode: WeightedModule.WeightedNode,
    )

    fun <T> dijkstra(
        source: Node,
        edgeWeight: (Edge) -> T,
        edgeComparator: Comparator<T>,
        weightAddition: (T, T) -> T,
    ): Map<Node, T> {
        data class Entry(val node: Node, val cost: T)

        val dist = mutableMapOf<Node, T>()
        val visited = mutableSetOf<Node>()

        val pq = java.util.PriorityQueue<Entry>(java.util.Comparator { a, b ->
            edgeComparator.compare(a.cost, b.cost)
        })

        // Initialize the frontier with direct neighbors of the source
        for (edge in adjacencyList[source] ?: emptyList()) {
            val neighbor = edge.sink
            val cost = edgeWeight(edge)
            val existing = dist[neighbor]
            if (existing == null || edgeComparator.compare(cost, existing) < 0) {
                dist[neighbor] = cost
                pq.add(Entry(neighbor, cost))
            }
        }

        // Standard Dijkstra's algorithm
        while (pq.isNotEmpty()) {
            val current = pq.poll()
            val u = current.node

            if (!visited.add(u)) continue

            val costToU = current.cost
            for (edge in adjacencyList[u] ?: emptyList()) {
                val v = edge.sink
                if (visited.contains(v)) continue

                val newCost = weightAddition(costToU, edgeWeight(edge))
                val oldCost = dist[v]
                if (oldCost == null || edgeComparator.compare(newCost, oldCost) < 0) {
                    dist[v] = newCost
                    pq.add(Entry(v, newCost))
                }
            }
        }

        return dist
    }

    fun topologicalSort(): List<Node> {
        // Compute in-degree for each node
        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        edges.forEach { edge -> inDegree[edge.sink] = (inDegree[edge.sink] ?: 0) + 1 }

        // Build outgoing adjacency from edges to ensure correct direction
        val outgoing: Map<Node, List<Edge>> = edges.groupBy { it.source }

        // Initialize queue with all zero in-degree nodes
        val queue: ArrayDeque<Node> = ArrayDeque()
        for (n in nodes) {
            if ((inDegree[n] ?: 0) == 0) {
                queue.add(n)
            }
        }

        // Kahn's algorithm
        val order = mutableListOf<Node>()
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            order.add(u)

            for (edge in outgoing[u] ?: emptyList()) {
                val v = edge.sink
                val newIn = (inDegree[v] ?: 0) - 1
                inDegree[v] = newIn
                if (newIn == 0) {
                    queue.add(v)
                }
            }
        }

        // If not all nodes were processed, there is a cycle
        if (order.size != nodes.size) {
            throw IllegalStateException("Graph contains a cycle; topological sort not possible.")
        }

        return order
    }

}

class ModuleAdjacencyList(
    val weightedModule: WeightedModule,
): AdjacencyList() {

    override val nodes: List<Node>
    override val edges: List<Edge>
    override val adjacencyList: Map<Node, List<Edge>>

    init {
        val nodes = weightedModule.nodes.map { node ->
            Node(node.weight, node)
        }.associateBy {it.underlyingNode}

        val edges = weightedModule.connections.map { connection ->
            Edge(connection.weight, nodes[connection.source]!!, nodes[connection.sink]!!, connection)
        }.groupBy { it.source }

        this.nodes = nodes.values.toList()
        this.edges = edges.values.flatten()
        this.adjacencyList = nodes.values.associateWith { edges[it] ?: emptyList() }
    }

    class Subgraph(
        val baseModuleAdjacencyList: ModuleAdjacencyList,
        nodeFilter: (Node) -> Boolean,
        edgeFilter: (Edge) -> Boolean,
    ): AdjacencyList() {
        override val nodes: List<Node> = baseModuleAdjacencyList.nodes.filter(nodeFilter)
        override val edges: List<Edge> = baseModuleAdjacencyList.edges.filter(edgeFilter)
        override val adjacencyList = nodes.associateWith { edges.filter { edge -> edge.sink == it } }
    }

    class Retiming(
        val baseModuleAdjacencyList: ModuleAdjacencyList,
        retimings: Map<Node, Int>,
    ) {

        val retimings = retimings.toMutableMap()

        fun forNode(node: Node, lag: Int) = retimings.set(node, lag)

        fun ofNode(node: Node): Int = retimings[node]!!

        fun ofEdge(edge: Edge): Int = edge.weight + ofNode(edge.sink) - ofNode(edge.source)

        fun retimedModule(): ModuleAdjacencyList {
            val newConnections = baseModuleAdjacencyList.edges.map { edge ->
                val connection = edge.underlyingConnection
                WeightedModule.WeightedConnection(
                    source = connection.source,
                    sink = connection.sink,
                    weight = ofEdge(edge),
                    connectionGroups = connection.connectionGroups,
                )
            }

            val newWeightedModule = WeightedModule(
                module = baseModuleAdjacencyList.weightedModule.module,
                nodes = baseModuleAdjacencyList.weightedModule.nodes,
                connections = newConnections,
            )

            return ModuleAdjacencyList(newWeightedModule)
        }

    }

    fun subgraph(
        nodeFilter: (Node) -> Boolean = { true },
        edgeFilter: (Edge) -> Boolean = { true },
    ) = Subgraph(this, nodeFilter, edgeFilter)

    fun retimed(retimings: Map<Node, Int>) = Retiming(this, retimings).retimedModule()

}