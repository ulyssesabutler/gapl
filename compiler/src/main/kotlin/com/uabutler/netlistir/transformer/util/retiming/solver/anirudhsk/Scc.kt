package retiming

/**
 * Iterative Tarjan SCC.
 *
 * Returns (componentCount, comp) where comp[v] is the component id of v.
 * Component ids are assigned in the order components complete, which is
 * *reverse* topological order of the condensation: for any edge u -> v with
 * comp[u] != comp[v], we have comp[v] < comp[u].
 */
fun stronglyConnectedComponents(n: Int, edges: List<Edge>): Pair<Int, IntArray> {
    val adj = Array(n) { ArrayList<Int>(4) }
    for (e in edges) adj[e.from].add(e.to)

    val index = IntArray(n) { -1 }
    val low = IntArray(n)
    val onStack = BooleanArray(n)
    val comp = IntArray(n) { -1 }
    var nextIndex = 0
    var nComp = 0
    val stack = ArrayDeque<Int>()
    val callV = ArrayDeque<Int>()
    val callI = ArrayDeque<Int>()

    for (s in 0 until n) {
        if (index[s] != -1) continue
        index[s] = nextIndex; low[s] = nextIndex; nextIndex++
        stack.addLast(s); onStack[s] = true
        callV.addLast(s); callI.addLast(0)
        while (callV.isNotEmpty()) {
            val v = callV.last()
            val i = callI.removeLast()
            if (i < adj[v].size) {
                callI.addLast(i + 1)
                val w = adj[v][i]
                if (index[w] == -1) {
                    index[w] = nextIndex; low[w] = nextIndex; nextIndex++
                    stack.addLast(w); onStack[w] = true
                    callV.addLast(w); callI.addLast(0)
                } else if (onStack[w] && index[w] < low[v]) {
                    low[v] = index[w]
                }
            } else {
                callV.removeLast()
                if (low[v] == index[v]) {
                    while (true) {
                        val w = stack.removeLast()
                        onStack[w] = false
                        comp[w] = nComp
                        if (w == v) break
                    }
                    nComp++
                }
                if (callV.isNotEmpty()) {
                    val p = callV.last()
                    if (low[v] < low[p]) low[p] = low[v]
                }
            }
        }
    }
    return Pair(nComp, comp)
}
