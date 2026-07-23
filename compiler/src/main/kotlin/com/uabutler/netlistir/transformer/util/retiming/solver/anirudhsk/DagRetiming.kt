package retiming

/** Edge with a Long weight that may be negative (SCC-adjusted cross edges). */
data class WEdge(val from: Int, val to: Int, val w: Long)

/**
 * One-pass retiming of a DAG for a target clock period c >= max delay.
 *
 * On a DAG there is no cycle on which the register count is conserved, so every
 * period c >= max d(v) is achievable, and the retiming is a single topological
 * sweep — no Bellman-Ford, no W/D matrices.
 *
 * For each vertex we compute a "settling time" phi(v) in a global unrolled
 * schedule:
 *
 *     phi(v) = max( d(v),  max over edges (u,v) of  phi(u) - c*w(u,v) + d(v) )
 *
 * and split it into a clock cycle number and an in-cycle arrival time:
 *
 *     r(v) = ceil(phi(v)/c) - 1        so that  s(v) = phi(v) - c*r(v)  is in (0, c]
 *     if s(v) < d(v): raise phi(v) to c*r(v) + d(v)   (residue must cover v's own delay)
 *
 * (Exception: phi(v) = 0 — a zero-delay vertex reached by nothing combinational,
 * e.g. an input interface — takes r = 0, s = 0, so it doesn't cost a register.)
 *
 * Invariants (proved by telescoping over zero-register paths):
 *   - w_r(e) = w(e) + r(head) - r(tail) >= 0 for every edge, even negative-weight ones;
 *   - if w_r(e) = 0 for e = (u,v) then s(v) >= s(u) + d(v), so any zero-register
 *     path has total delay <= s(last) <= c.
 *
 * Edge weights may be negative; the graph must be acyclic.
 */
fun dagRetime(delays: LongArray, edges: List<WEdge>, c: Long): IntArray {
    val n = delays.size
    require(c >= 1) { "clock period must be positive" }
    require(delays.all { it in 0..c }) { "target period must be >= every vertex delay" }

    // Topological order over all edges.
    val indeg = IntArray(n)
    val inEdges = Array(n) { ArrayList<WEdge>(4) }
    for (e in edges) {
        inEdges[e.to].add(e)
        indeg[e.to]++
    }
    val outAdj = Array(n) { ArrayList<Int>(4) }
    for (e in edges) outAdj[e.from].add(e.to)
    val queue = ArrayDeque<Int>()
    for (v in 0 until n) if (indeg[v] == 0) queue.add(v)
    val topo = ArrayList<Int>(n)
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        topo.add(u)
        for (v in outAdj[u]) if (--indeg[v] == 0) queue.add(v)
    }
    require(topo.size == n) { "graph is not a DAG" }

    val phi = LongArray(n)
    val r = LongArray(n)
    for (v in topo) {
        var p = delays[v]
        for (e in inEdges[v]) {
            val cand = phi[e.from] - c * e.w + delays[v]
            if (cand > p) p = cand
        }
        val rv: Long
        if (p == 0L) {
            // Zero-delay vertex with nothing combinational arriving (e.g. an
            // input interface): take r = 0, s = 0 rather than r = -1, s = c,
            // so it can feed logic combinationally without costing a register.
            // Legality and the period argument still hold: an edge out of such
            // a vertex contributes arrival 0, and any phi > 0 successor gets
            // r >= 0 = r(v) - 0.
            rv = 0
        } else {
            rv = ceilDiv(p, c) - 1        // s(v) = p - c*rv is in (0, c]
            val s = p - c * rv
            if (s < delays[v]) p = c * rv + delays[v]
        }
        phi[v] = p
        r[v] = rv
    }

    // A uniform shift of r changes no edge weight; normalize the minimum to 0.
    val shift = r.minOrNull() ?: 0L
    return IntArray(n) { Math.toIntExact(r[it] - shift) }
}

private fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)
