package retiming

/**
 * Algorithm FEAS from Leiserson-Saxe: iterative relaxation that finds a legal
 * retiming with clock period <= c, or proves none exists.
 *
 * Repeat |V|-1 times: compute arrival times, then increment r(v) for every v
 * whose arrival time exceeds c. Feasible iff the final configuration meets c.
 */
fun feas(circuit: Circuit, c: Long): IntArray? {
    val n = circuit.n
    val r = IntArray(n)
    repeat(maxOf(n - 1, 0)) {
        val delta = circuit.arrivals(r) ?: return null
        var changed = false
        for (v in 0 until n) {
            if (delta[v] > c) {
                r[v]++
                changed = true
            }
        }
        if (!changed) return r
    }
    val delta = circuit.arrivals(r) ?: return null
    return if (delta.all { it <= c }) r else null
}
