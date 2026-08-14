package graph

import com.uabutler.util.Logger
import graph.PortHierarchicalTestUtil.createPortGraph
import graph.PortHierarchicalTestUtil.edgeWeight
import graph.PortHierarchicalTestUtil.solve
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PerPortHierarchicalMinimalRegisterSolverTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    /**
     * A child with a long path from one input port and a short path from another, where the parent's
     * feedback loop runs through the *short* port.
     *
     * This is the shape that makes `HierarchicalMinimalRegisterSolver` infeasible at every clock
     * period, and it is netfpga CMS's shape: pipelining the long path forces the same registers onto
     * the short path, and the loop cannot absorb them because a retiming cannot change a loop's
     * register count. Per-port lags let the two ports diverge, so the loop is untouched.
     */
    private fun childWithLongAndShortPort(): PortGraph = createPortGraph(
        name = "updater",
        edgeList = listOf(
            Edge("data", "h1", 0),
            Edge("h1", "h2", 0),
            Edge("h2", "h3", 0),
            Edge("h3", "combine", 0),
            Edge("state", "combine", 0),
            Edge("combine", "out", 0),
        ),
        inputPorts = listOf("data", "state"),
        outputPorts = listOf("out"),
        leafWeights = mapOf("h1" to 1, "h2" to 1, "h3" to 1, "combine" to 1),
    )

    @Test
    fun `a parent loop through a child survives the child being pipelined`() {
        val child = childWithLongAndShortPort()

        val loopReturn = Edge("u.out", "u.state", 1)
        val parent = createPortGraph(
            name = "top",
            edgeList = listOf(
                Edge("i", "u.data", 0),
                Edge("u.out", "o", 0),
                loopReturn,
            ),
            inputPorts = listOf("i"),
            outputPorts = listOf("o"),
            childInstances = listOf(ChildInstanceSketch("u", child)),
        )

        // The child's data path is four nodes of weight 1, so period 2 forces it to pipeline. The
        // whole-module solver cannot do this at all; per-port lags make it routine.
        val results = solve(listOf(child, parent), targetClockPeriod = 2)
        assertNotNull(results, "period 2 should be achievable with per-port lags")

        // The loop's register count is invariant under retiming, so the return edge must still carry
        // exactly the register it started with - the child's pipelining must not have leaked into it.
        assertEquals(
            1,
            edgeWeight(results.getValue(parent), loopReturn),
            "the parent's feedback loop must keep exactly its original register",
        )
    }

    @Test
    fun `ports of a child may take different lags`() {
        val child = childWithLongAndShortPort()

        val parent = createPortGraph(
            name = "top",
            edgeList = listOf(
                Edge("i", "u.data", 0),
                Edge("i", "u.state", 0),
                Edge("u.out", "o", 0),
            ),
            inputPorts = listOf("i"),
            outputPorts = listOf("o"),
            childInstances = listOf(ChildInstanceSketch("u", child)),
        )

        val results = solve(listOf(child, parent), targetClockPeriod = 2)
        assertNotNull(results)

        val retimedChild = results.getValue(child)
        val dataLag = retimedChild.edges.first { it.value == Edge("data", "h1", 0).value() }
        val stateLag = retimedChild.edges.first { it.value == Edge("state", "combine", 0).value() }

        // The point of the whole exercise: the long path is pipelined while the short one is not
        // forced to match register-for-register at its own port.
        val childRegisters = retimedChild.edges.sumOf { it.weight }
        assertEquals(
            1,
            childRegisters,
            "child should need exactly one register to meet period 2, not one per input port " +
                "(data->h1=${dataLag.weight}, state->combine=${stateLag.weight})",
        )
    }

    /**
     * A top-level module's ports must stay mutually aligned even though inner modules' ports may
     * skew - the environment gets one pipeline stage for all inputs and one for all outputs.
     *
     * Only testable because root alignment is an explicit equality constraint rather than a
     * `VirtualIONode` type check; the whole-module solver's harness is `String`-valued, so its
     * equivalent constraint never fires in tests at all.
     */
    @Test
    fun `top level ports stay aligned`() {
        val top = createPortGraph(
            name = "top",
            edgeList = listOf(
                // A short path and a long one, reconverging. Retiming will want to pipeline the long
                // one; the two output ports must still end up at the same pipeline stage.
                Edge("i1", "slow1", 0),
                Edge("slow1", "slow2", 0),
                Edge("slow2", "o1", 0),
                Edge("i2", "fast", 0),
                Edge("fast", "o2", 0),
            ),
            inputPorts = listOf("i1", "i2"),
            outputPorts = listOf("o1", "o2"),
            leafWeights = mapOf("slow1" to 1, "slow2" to 1, "fast" to 1),
        )

        val results = solve(listOf(top), targetClockPeriod = 1)
        assertNotNull(results, "period 1 should be achievable")

        val retimed = results.getValue(top)
        // i1 -> o1 traverses two weight-1 nodes, so at period 1 it needs a register; i2 -> o2 needs
        // none on its own, but must gain one anyway to keep o2 aligned with o1.
        val slowPathRegisters = listOf(Edge("i1", "slow1", 0), Edge("slow1", "slow2", 0), Edge("slow2", "o1", 0))
            .sumOf { edgeWeight(retimed, it) }
        val fastPathRegisters = listOf(Edge("i2", "fast", 0), Edge("fast", "o2", 0))
            .sumOf { edgeWeight(retimed, it) }

        assertEquals(
            slowPathRegisters,
            fastPathRegisters,
            "both output ports must sit at the same pipeline stage at the top level",
        )
    }
}
