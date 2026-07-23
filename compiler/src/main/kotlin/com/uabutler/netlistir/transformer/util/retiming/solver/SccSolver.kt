package com.uabutler.netlistir.transformer.util.retiming.solver

import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.Retiming
import com.uabutler.util.Logger
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph
import java.util.IdentityHashMap
import retiming.Circuit as SccCircuit
import retiming.Edge as SccEdge
import retiming.retimeByScc

/**
 * Adapter around the vendored SCC-condensation retiming algorithm (retiming.retimeByScc) -
 * feasibility-only, fast, analogous in role to [FastSolver]: finds *a* legal retiming meeting a
 * target clock period, does not minimize register count.
 *
 * Known gap, not fixed here: unlike [MinimalRegisterSolver], this does not enforce the
 * zero-register VirtualIONode boundary invariant, since retimeByScc has no equality-constraint
 * mechanism. See brainstorming/todo.md > ## Retiming.
 */
class SccSolver<G, N, E>(problem: MonolithicRetimingProblem<G, N, E>) : MonolithicSolver<G, N, E>(problem) {

    private val graph = problem.graph

    override fun solveOrNull(
        targetClockPeriod: Int?
    ): MonolithicRetimingProblem<G, N, E>? = Logger.run("Retiming for clock period $targetClockPeriod via SCC condensation", Logger.Level.DEBUG) {
        if (targetClockPeriod == null) return@run problem

        val nodesList = graph.nodes.toList()
        // Node<N> is a structural data class (equals/hashCode by value), so a regular HashMap
        // could merge coincidentally-equal-but-distinct nodes - must key by identity, same reason
        // the Graph base class itself uses IdentityHashMap internally.
        val indexOf = IdentityHashMap<WeightedGraph.Node<N>, Int>()
        nodesList.forEachIndexed { index, node -> indexOf[node] = index }

        val circuit = try {
            SccCircuit(
                delays = IntArray(nodesList.size) { nodesList[it].weight },
                edges = graph.edges.map { SccEdge(indexOf.getValue(it.source), indexOf.getValue(it.sink), it.weight) },
            )
        } catch (e: IllegalArgumentException) {
            throw Exception("SccSolver requires non-negative node delays and edge weights", e)
        }

        val lags = retimeByScc(circuit, targetClockPeriod.toLong())
            ?: return@run null.also { Logger.debug { "Did not find feasible solution" } }

        val retiming = Retiming(
            graph = graph,
            graphFactory = { nodes, edges -> LeisersonCircuitGraph(graph.value, nodes, edges) },
        )
        nodesList.forEachIndexed { index, node -> retiming.setNodeLag(node, lags[index]) }

        Logger.debug { "Found feasible solution" }
        return@run MonolithicRetimingProblem(retiming.generateNewCircuit())
    }

}
