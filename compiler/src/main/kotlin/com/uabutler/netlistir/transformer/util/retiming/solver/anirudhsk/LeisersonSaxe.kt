package retiming

/**
 * Reference implementation: the feasibility test at the heart of algorithm OPT1
 * from Leiserson & Saxe, "Retiming Synchronous Circuitry" (Algorithmica, 1991).
 *
 *  - W(u,v) = minimum register count over all paths u ~> v.
 *  - D(u,v) = maximum path delay among the paths realizing W(u,v).
 *    Computed by all-pairs shortest paths with lexicographic edge weight
 *    (w(e), -d(tail)); then D(u,v) = d(v) - secondComponent.
 *  - A period c is feasible iff Bellman-Ford finds no negative cycle in the
 *    difference-constraint system
 *        r(u) - r(v) <= w(e)          for every edge e = (u,v)
 *        r(u) - r(v) <= W(u,v) - 1    whenever D(u,v) > c
 *    in which case the shortest-path distances are a witness retiming.
 */
private const val INF = Long.MAX_VALUE / 4

class WDMatrices(val w: Array<LongArray>, val d: Array<LongArray>, val reach: Array<BooleanArray>)

fun computeWD(circuit: Circuit): WDMatrices {
    val n = circuit.n
    val w = Array(n) { LongArray(n) { INF } }
    val s = Array(n) { LongArray(n) { INF } } // sum of -d(tail) along the lex-shortest path
    val reach = Array(n) { BooleanArray(n) }
    for (v in 0 until n) {
        w[v][v] = 0; s[v][v] = 0; reach[v][v] = true // the trivial path: W=0, D=d(v)
    }
    for (e in circuit.edges) {
        val ew = e.w.toLong()
        val es = -circuit.delays[e.from].toLong()
        if (!reach[e.from][e.to] || ew < w[e.from][e.to] ||
            (ew == w[e.from][e.to] && es < s[e.from][e.to])
        ) {
            w[e.from][e.to] = ew; s[e.from][e.to] = es; reach[e.from][e.to] = true
        }
    }
    for (k in 0 until n) for (u in 0 until n) {
        if (!reach[u][k]) continue
        val wuk = w[u][k]; val suk = s[u][k]
        for (v in 0 until n) {
            if (!reach[k][v]) continue
            val nw = wuk + w[k][v]; val ns = suk + s[k][v]
            if (!reach[u][v] || nw < w[u][v] || (nw == w[u][v] && ns < s[u][v])) {
                w[u][v] = nw; s[u][v] = ns; reach[u][v] = true
            }
        }
    }
    val d = Array(n) { u -> LongArray(n) { v -> if (reach[u][v]) circuit.delays[v] - s[u][v] else -1 } }
    return WDMatrices(w, d, reach)
}

/** Bellman-Ford feasibility check for period c. Returns a satisfying retiming or null. */
fun bellmanFordFeasible(circuit: Circuit, wd: WDMatrices, c: Long): IntArray? {
    val n = circuit.n
    // Constraint x_u - x_v <= k relaxes as dist(u) <= dist(v) + k.
    val consFrom = ArrayList<Int>()
    val consTo = ArrayList<Int>()
    val consW = ArrayList<Long>()
    for (e in circuit.edges) {
        consFrom.add(e.to); consTo.add(e.from); consW.add(e.w.toLong())
    }
    for (u in 0 until n) for (v in 0 until n) {
        if (wd.reach[u][v] && wd.d[u][v] > c) {
            consFrom.add(v); consTo.add(u); consW.add(wd.w[u][v] - 1)
        }
    }
    val dist = LongArray(n) // implicit source with 0-weight edge to every vertex
    val m = consFrom.size
    for (round in 0..n) {
        var changed = false
        for (i in 0 until m) {
            val nd = dist[consFrom[i]] + consW[i]
            if (nd < dist[consTo[i]]) {
                dist[consTo[i]] = nd
                changed = true
            }
        }
        if (!changed) {
            val min = dist.minOrNull() ?: 0L
            return IntArray(n) { Math.toIntExact(dist[it] - min) }
        }
    }
    return null // still relaxing after n rounds: negative cycle, infeasible
}

/** Reference: decide feasibility of period c (with a witness) the classical way. */
fun referenceFeasible(circuit: Circuit, c: Long): IntArray? {
    if (circuit.n == 0) return IntArray(0)
    return bellmanFordFeasible(circuit, computeWD(circuit), c)
}
