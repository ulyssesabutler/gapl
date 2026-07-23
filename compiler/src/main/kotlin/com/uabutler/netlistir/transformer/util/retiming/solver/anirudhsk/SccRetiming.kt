package retiming

/**
 * The SCC-condensation retiming algorithm.
 *
 * The clock period c is exogenous: given c, find a legal retiming with clock
 * period <= c, or return null if none exists. Feasibility decomposes exactly
 * by strongly connected component, because cycles — the only real obstruction
 * to retiming — live entirely inside SCCs, and paths between two vertices of
 * the same SCC never leave it:
 *
 *   - c is feasible for the whole circuit  iff  c >= max_v d(v) and c is
 *     feasible for every nontrivial SCC subcircuit on its own;
 *   - the DAG between SCCs imposes no constraint (retiming there is pipelining).
 *
 * The construction:
 *
 * 1. Find SCCs (Tarjan).
 * 2. Retime each nontrivial SCC internally for period c with FEAS; if any SCC
 *    fails, the period is infeasible.
 * 3. Condense: one node per SCC. A nontrivial SCC becomes an opaque node with
 *    delay c, which forces the DAG pass to keep at least one register on its
 *    boundary edges, isolating its internal combinational paths. Cross-edge
 *    weights are adjusted by the internal retiming: w'(e) = w(e) + rInt(head) -
 *    rInt(tail) (this may go negative; the DAG pass handles that).
 * 4. One-pass DAG retiming of the condensation at period c; the final retiming
 *    is r(v) = rInt(v) + rCond(scc(v)) — a per-SCC constant shift never changes
 *    SCC-internal edge weights.
 */
fun retimeByScc(circuit: Circuit, c: Long): IntArray? {
    val n = circuit.n
    if (n == 0) return IntArray(0)
    if (circuit.delays.any { it > c }) return null // a single vertex already misses c
    if (c == 0L) return IntArray(n) // all delays are 0: the identity retiming has period 0

    val (nComp, comp) = stronglyConnectedComponents(n, circuit.edges)

    val memberCount = IntArray(nComp)
    for (v in 0 until n) memberCount[comp[v]]++
    val internalEdges = Array(nComp) { ArrayList<Edge>() }
    val crossEdges = ArrayList<Edge>()
    for (e in circuit.edges) {
        if (comp[e.from] == comp[e.to]) internalEdges[comp[e.from]].add(e) else crossEdges.add(e)
    }
    // Nontrivial = contains a cycle (more than one vertex, or a self-loop).
    val nontrivial = BooleanArray(nComp) { memberCount[it] > 1 || internalEdges[it].isNotEmpty() }

    // Local subcircuit for each nontrivial SCC.
    val localIndex = IntArray(n) { -1 }
    val members = Array(nComp) { IntArray(0) }
    run {
        val fill = IntArray(nComp)
        for (k in 0 until nComp) members[k] = IntArray(memberCount[k])
        for (v in 0 until n) {
            val k = comp[v]
            localIndex[v] = fill[k]
            members[k][fill[k]++] = v
        }
    }

    // Steps 1-2: internal retimings at c; any failure means c is infeasible.
    val rInt = IntArray(n)
    for (k in 0 until nComp) {
        if (!nontrivial[k]) continue
        val subDelays = IntArray(memberCount[k]) { circuit.delays[members[k][it]] }
        val subEdges = internalEdges[k].map { Edge(localIndex[it.from], localIndex[it.to], it.w) }
        val rSub = feas(Circuit(subDelays, subEdges), c) ?: return null
        for (i in members[k].indices) rInt[members[k][i]] = rSub[i]
    }

    // Step 3: condensation.
    val nodeDelay = LongArray(nComp) {
        if (nontrivial[it]) c else circuit.delays[members[it][0]].toLong()
    }
    val condEdges = crossEdges.map {
        WEdge(comp[it.from], comp[it.to], (it.w + rInt[it.to] - rInt[it.from]).toLong())
    }

    // Step 4: one-pass DAG retiming, then combine.
    val rCond = dagRetime(nodeDelay, condEdges, c)
    return IntArray(n) { rInt[it] + rCond[comp[it]] }
}
