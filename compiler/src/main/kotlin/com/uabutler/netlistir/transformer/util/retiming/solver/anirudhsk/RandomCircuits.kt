package retiming

import kotlin.random.Random

/**
 * Random well-formed circuits.
 *
 * Vertices are laid out in a random hidden order; "forward" edges (w may be 0)
 * follow that order and "back" edges always carry at least one register, so
 * every cycle carries a register. Self-loops (w >= 1) and occasional parallel
 * edges are thrown in too.
 */
fun randomCircuit(rng: Random, maxN: Int, allowCycles: Boolean = true): Circuit {
    val n = rng.nextInt(1, maxN + 1)
    val delays = IntArray(n) { if (rng.nextInt(10) == 0) 0 else rng.nextInt(1, 13) }
    if (delays.all { it == 0 }) delays[rng.nextInt(n)] = rng.nextInt(1, 13)

    val order = (0 until n).shuffled(rng)
    val pForward = 0.05 + rng.nextDouble() * 0.35
    val pBack = if (allowCycles) rng.nextDouble() * 0.30 else 0.0

    val edges = ArrayList<Edge>()
    fun forwardWeight() = when (rng.nextInt(6)) {
        0 -> 1; 1 -> 2; else -> 0
    }
    for (i in 0 until n) for (j in i + 1 until n) {
        if (rng.nextDouble() < pForward) {
            edges.add(Edge(order[i], order[j], forwardWeight()))
            if (rng.nextInt(50) == 0) edges.add(Edge(order[i], order[j], forwardWeight()))
        }
        if (rng.nextDouble() < pBack) {
            edges.add(Edge(order[j], order[i], 1 + rng.nextInt(2)))
        }
    }
    if (allowCycles) {
        for (v in 0 until n) if (rng.nextInt(20) == 0) edges.add(Edge(v, v, 1 + rng.nextInt(2)))
    }
    return Circuit(delays, edges)
}

/**
 * Random well-formed circuit with many *small* SCCs — the structure the SCC
 * algorithm is designed for (and the common shape of real hardware).
 *
 * Vertices are partitioned into contiguous blocks of 1..maxBlock vertices.
 * Each multi-vertex block is made strongly connected by a forward chain plus a
 * registered closing edge, with random extra chords (index-decreasing chords
 * always carry a register, so every cycle is registered). Cross edges only go
 * from lower-indexed to higher-indexed vertices, so the SCCs are exactly the
 * blocks.
 */
fun randomClusteredCircuit(rng: Random, n: Int, maxBlock: Int = 8): Circuit {
    val delays = IntArray(n) { if (rng.nextInt(10) == 0) 0 else rng.nextInt(1, 13) }
    if (delays.all { it == 0 }) delays[rng.nextInt(n)] = rng.nextInt(1, 13)

    val edges = ArrayList<Edge>()
    var start = 0
    while (start < n) {
        val size = minOf(rng.nextInt(1, maxBlock + 1), n - start)
        if (size > 1) {
            for (i in 0 until size - 1) {
                edges.add(Edge(start + i, start + i + 1, if (rng.nextInt(4) == 0) 1 else 0))
            }
            edges.add(Edge(start + size - 1, start, 1 + rng.nextInt(2)))
            repeat(size) {
                val a = start + rng.nextInt(size)
                val b = start + rng.nextInt(size)
                if (a < b) edges.add(Edge(a, b, if (rng.nextInt(3) == 0) 1 else 0))
                if (a > b) edges.add(Edge(a, b, 1 + rng.nextInt(2)))
            }
        } else if (rng.nextInt(10) == 0) {
            edges.add(Edge(start, start, 1 + rng.nextInt(2)))
        }
        start += size
    }
    // Forward-only cross edges (mostly unregistered, so pipelining has real work to do).
    repeat(3 * n / 2) {
        val u = rng.nextInt(n - 1)
        val v = u + 1 + rng.nextInt(n - 1 - u)
        edges.add(Edge(u, v, if (rng.nextInt(3) == 0) 1 + rng.nextInt(2) else 0))
    }
    return Circuit(delays, edges)
}
