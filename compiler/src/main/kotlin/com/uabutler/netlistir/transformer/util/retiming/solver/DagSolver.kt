package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.Retiming
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import java.util.IdentityHashMap
import retiming.WEdge
import retiming.dagRetime

/**
 * Adapter around the vendored one-pass DAG retiming algorithm (retiming.dagRetime).
 *
 * Narrower than [SccSolver]/[FastSolver]: dagRetime requires the *entire* circuit to be acyclic,
 * not just well-formed (a cycle with a register protecting it - the normal, expected shape of any
 * real stateful/feedback circuit - is out of scope; only pure combinational-pipeline circuits with
 * no loops of any kind apply). When that precondition holds, retiming is trivial and always
 * succeeds for any period >= the largest node delay (no Bellman-Ford, one topological sweep) - see
 * brainstorming/todo.md > ## Retiming.
 *
 * Like [SccSolver], this does not enforce the zero-register VirtualIONode boundary invariant
 * [MinimalRegisterSolver] enforces.
 */
class DagSolver<G, N, E>(problem: MonolithicRetimingProblem<G, N, E>) : MonolithicSolver<G, N, E>(problem) {

    private val graph = problem.graph

    override fun solveOrNull(
        targetClockPeriod: Int?
    ): MonolithicRetimingProblem<G, N, E>? = Logger.run("Retiming for clock period $targetClockPeriod via DAG sweep", Logger.Level.DEBUG) {
        if (targetClockPeriod == null) return@run problem

        val nodesList = graph.nodes.toList()
        // Node<N> is a structural data class (equals/hashCode by value), so a regular HashMap
        // could merge coincidentally-equal-but-distinct nodes - must key by identity, same reason
        // the Graph base class itself uses IdentityHashMap internally.
        val indexOf = IdentityHashMap<WeightedGraph.Node<N>, Int>()
        nodesList.forEachIndexed { index, node -> indexOf[node] = index }

        // dagRetime's own `require`s treat both of these as hard preconditions (c >= 1, every
        // delay <= c) and throw rather than returning a "not feasible" signal. Every other solver
        // here treats "period too small" as ordinary infeasibility, so check for it up front and
        // return null - leaving only the actual "not a DAG" precondition to surface as an
        // exception below, since that's a real solver/graph-shape mismatch, not infeasibility.
        if (targetClockPeriod < 1 || nodesList.any { it.weight > targetClockPeriod }) {
            Logger.debug { "Did not find feasible solution" }
            return@run null
        }

        val delays = LongArray(nodesList.size) { nodesList[it].weight.toLong() }
        val dagEdges = graph.edges.map { WEdge(indexOf.getValue(it.source), indexOf.getValue(it.sink), it.weight.toLong()) }

        val lags = try {
            dagRetime(delays, dagEdges, targetClockPeriod.toLong())
        } catch (e: IllegalArgumentException) {
            throw Exception(
                "DagSolver requires an acyclic circuit - dagRetime only handles DAGs, not general " +
                    "well-formed circuits with register-protected cycles",
                e,
            )
        }

        val retiming = Retiming(
            graph = graph,
            graphFactory = { nodes, edges -> LeisersonCircuitGraph(graph.value, nodes, edges) },
        )
        nodesList.forEachIndexed { index, node -> retiming.setNodeLag(node, lags[index]) }

        Logger.debug { "Found feasible solution" }
        return@run MonolithicRetimingProblem(retiming.generateNewCircuit())
    }

}
