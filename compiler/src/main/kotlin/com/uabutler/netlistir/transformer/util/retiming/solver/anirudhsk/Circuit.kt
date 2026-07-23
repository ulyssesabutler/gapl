package retiming

/**
 * A synchronous circuit in the Leiserson-Saxe model.
 *
 * Vertices are functional elements with propagation delay d(v) >= 0.
 * Each edge u -> v carries w(e) >= 0 registers.
 * A circuit is well-formed if every directed cycle carries at least one register
 * (equivalently: the zero-register subgraph is acyclic).
 */
data class Edge(val from: Int, val to: Int, val w: Int)

class Circuit(val delays: IntArray, val edges: List<Edge>) {
    val n: Int get() = delays.size

    init {
        require(delays.all { it >= 0 }) { "delays must be non-negative" }
        require(edges.all { it.w >= 0 }) { "edge weights must be non-negative" }
    }

    /** Register count of edge e after applying retiming r: w_r(e) = w(e) + r(head) - r(tail). */
    fun retimedWeight(e: Edge, r: IntArray): Int = e.w + r[e.to] - r[e.from]

    /** A retiming is legal iff no edge ends up with a negative register count. */
    fun isLegalRetiming(r: IntArray): Boolean = edges.all { retimedWeight(it, r) >= 0 }

    /**
     * Arrival times Delta(v) under retiming r (algorithm CP of Leiserson-Saxe):
     * Delta(v) = d(v) + max over zero-register in-edges (u,v) of Delta(u).
     * Returns null if the zero-register subgraph contains a cycle (ill-formed circuit).
     */
    fun arrivals(r: IntArray = IntArray(n)): LongArray? {
        val indeg = IntArray(n)
        val zeroOut = Array(n) { ArrayList<Int>(4) }
        for (e in edges) {
            if (retimedWeight(e, r) == 0) {
                zeroOut[e.from].add(e.to)
                indeg[e.to]++
            }
        }
        val delta = LongArray(n) { delays[it].toLong() }
        val queue = ArrayDeque<Int>()
        for (v in 0 until n) if (indeg[v] == 0) queue.add(v)
        var processed = 0
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            processed++
            for (v in zeroOut[u]) {
                if (delta[u] + delays[v] > delta[v]) delta[v] = delta[u] + delays[v]
                if (--indeg[v] == 0) queue.add(v)
            }
        }
        return if (processed == n) delta else null
    }

    /** Clock period Phi(G_r) = longest purely-combinational path delay under retiming r. */
    fun clockPeriod(r: IntArray = IntArray(n)): Long {
        val delta = arrivals(r) ?: error("zero-register cycle: circuit is not well-formed")
        return delta.maxOrNull() ?: 0L
    }
}
