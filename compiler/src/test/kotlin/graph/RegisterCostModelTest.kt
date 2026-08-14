package graph

import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.solver.MinimalRegisterSolver
import com.uabutler.util.Logger
import com.uabutler.util.graph.WeightedGraph
import graph.TestUtil.createGraph
import graph.TestUtil.getCorrespondingEdge
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cost model [MinimalRegisterSolver] optimises: flip-flops, not edges.
 *
 * These pin the two properties the plain sum-of-edge-weights objective did not have. A register on a
 * wide bus has to cost more than one on a narrow wire, and a bit whose consumers all sit at the same
 * depth has to be charged once rather than once per consumer - because
 * `NetlistLeisersonCircuitConverter.addSharedWeightedConnections` emits exactly one shift register
 * per driving bit, so anything else prices hardware that is never built.
 */
class RegisterCostModelTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    /** Names one bit, identified by object identity the way a netlist `OutputWire` is. */
    private class Bit(val name: String) {
        override fun toString() = name
    }

    private fun Graph.minimizeWith(
        clockPeriod: Int,
        bits: Map<String, List<Bit>>,
    ): Graph {
        val solver = MinimalRegisterSolver(
            MonolithicRetimingProblem(this),
            edgeSourceBits = { edge: WeightedGraph.Edge<String, String> -> bits[edge.value] ?: emptyList() },
        )
        return solver.solveOrNull(clockPeriod)!!.graph
    }

    // a -> b -> c, every node one unit of delay, so at period 2 the a-to-c path needs exactly one
    // register and it can go on either edge. The only thing that breaks the tie is what the edges cost.
    private val chain = listOf(EdgeSketch("a", "b", 0), EdgeSketch("b", "c", 0))

    private fun assertChainRegistersTheNarrowEdge(wideEdge: EdgeSketch, narrowEdge: EdgeSketch) {
        val graph = createGraph(name = "chain", edgeList = chain)

        val retimed = graph.minimizeWith(
            clockPeriod = 2,
            bits = mapOf(
                wideEdge.value() to List(8) { Bit("wide$it") },
                narrowEdge.value() to listOf(Bit("narrow")),
            ),
        )

        assertEquals(
            0,
            getCorrespondingEdge(retimed.edges, wideEdge).weight,
            "the 8-bit edge should stay unregistered",
        )
        assertEquals(
            1,
            getCorrespondingEdge(retimed.edges, narrowEdge).weight,
            "the 1-bit edge should carry the register the clock period forces",
        )
    }

    @Test
    fun `a register goes on the narrow edge, not the wide one`() {
        assertChainRegistersTheNarrowEdge(wideEdge = chain[0], narrowEdge = chain[1])
    }

    @Test
    fun `a register goes on the narrow edge regardless of which side it is on`() {
        // Same graph, widths swapped, so this cannot pass by accidentally always picking one side.
        assertChainRegistersTheNarrowEdge(wideEdge = chain[1], narrowEdge = chain[0])
    }

    /**
     * s -> a, then a fans out to b, c and d. At period 2 every one of a's consumers needs a register,
     * so the retiming either puts one register on the 2-bit edge in front of the fanout, or one on
     * each of the three branches behind it.
     *
     * Behind the fanout is three edges but only *one* driving bit, and one shift register is what
     * gets emitted - so with sharing modelled it costs 1 and beats the 2 in front. Without sharing
     * the same choice is priced at 3, and the solver moves the register in front instead, buying two
     * flip-flops to avoid one.
     */
    private val fanOutToA = EdgeSketch("s", "a", 0)
    private val fanOutBranches = listOf(EdgeSketch("a", "b", 0), EdgeSketch("a", "c", 0), EdgeSketch("a", "d", 0))
    private val fanOut = listOf(fanOutToA) + fanOutBranches

    @Test
    fun `fanout at one depth is charged once, so the register stays behind the fanout`() {
        val graph = createGraph(name = "fanout", edgeList = fanOut)
        val shared = Bit("a-out")

        val retimed = graph.minimizeWith(
            clockPeriod = 2,
            bits = mapOf(fanOutToA.value() to listOf(Bit("s0"), Bit("s1"))) +
                // Every branch carries the same driving bit, so they share one shift register.
                fanOutBranches.associate { it.value() to listOf(shared) },
        )

        assertEquals(0, getCorrespondingEdge(retimed.edges, fanOutToA).weight, "s -> a should stay unregistered")
        fanOutBranches.forEach { branch ->
            assertEquals(1, getCorrespondingEdge(retimed.edges, branch).weight, "${branch.value()} should be registered")
        }
    }

    @Test
    fun `without sharing the same graph pays to move the register in front of the fanout`() {
        val graph = createGraph(name = "fanout", edgeList = fanOut)

        val retimed = graph.minimizeWith(
            clockPeriod = 2,
            bits = mapOf(fanOutToA.value() to listOf(Bit("s0"), Bit("s1"))) +
                // Distinct bits: the branches now look like three independent registers.
                fanOutBranches.associate { it.value() to listOf(Bit("${it.sink}-in")) },
        )

        assertEquals(1, getCorrespondingEdge(retimed.edges, fanOutToA).weight, "s -> a should absorb the register")
        fanOutBranches.forEach { branch ->
            assertEquals(0, getCorrespondingEdge(retimed.edges, branch).weight, "${branch.value()} should stay unregistered")
        }
    }
}
