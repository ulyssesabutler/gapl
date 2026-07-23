package retiming

/**
 * Repipelining: the circuit computes a stateful (cycles = state) function from
 * a designated input vertex to a designated output vertex. To reach the target
 * clock period, registers may be inserted on input-to-output paths — raising
 * the I/O latency — and existing registers may be moved (plain retiming);
 * cycles never gain or lose registers, so the stateful behavior is preserved.
 *
 * This is exactly host-less retiming plus bookkeeping, thanks to two
 * consequences of the path invariant w_r(p) = w(p) + r(head) - r(tail):
 *
 *   - taking the path around any cycle (head = tail), its register count is
 *     unchanged by EVERY retiming — the "no new registers on stateful loops"
 *     rule cannot be violated. (c-slowing, which multiplies cycle register
 *     counts, is the transformation that would relax this; out of scope.)
 *   - every input-to-output path gains exactly r(output) - r(input) registers,
 *     so the added latency is a single well-defined number, independent of
 *     which path a signal takes.
 *
 * The retiming is normalized so that r(input) = 0 (the input interface stays
 * put); the reported latency is then just r(output). Latency is not minimized
 * (see the refinements list) — it is whatever the SCC construction produces.
 */
class RepipelineResult(val latency: Int, val r: IntArray)

fun repipeline(circuit: Circuit, input: Int, output: Int, c: Long): RepipelineResult? {
    require(input in 0 until circuit.n && output in 0 until circuit.n) {
        "interface vertices must exist in the circuit"
    }
    require(input != output) { "input and output must be distinct" }
    val r = retimeByScc(circuit, c) ?: return null
    val shift = r[input]
    for (v in r.indices) r[v] -= shift
    return RepipelineResult(r[output], r)
}
