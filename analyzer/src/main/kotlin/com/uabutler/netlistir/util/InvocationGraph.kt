package com.uabutler.netlistir.util

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.util.graph.UnweightedGraph

class InvocationGraph<T : Module>(val modules: Collection<T>) {

    private val moduleByInvocation = modules.associateBy { it.invocation }
    private val graph: UnweightedGraph<Module.Invocation, ModuleInvocationNode>

    init {
        val moduleNodes = modules.map { UnweightedGraph.Node(it.invocation) }.associateBy { it.value }
        val nodes = moduleNodes.values.toList()
        val edges = modules.flatMap { currentModule ->
            val source = currentModule.invocation
            val sinks = currentModule.getBodyNodes()
                .filterIsInstance<ModuleInvocationNode>()
                .associateWith { it.invocation }

            sinks.map { (node, sink) ->
                UnweightedGraph.Edge(
                    source = moduleNodes[source]!!,
                    sink = moduleNodes[sink]!!,
                    value = node,
                )
            }
        }

        graph = UnweightedGraph(nodes, edges)
    }

    fun rootModules(): List<T> = graph.rootNodes().map { moduleByInvocation[it.value]!! }

    fun topologicalSort(): List<T> {
        return graph.topologicalSort().map { moduleByInvocation[it.value]!! }
    }

    data class Cycle<T>(
        // The functions (with their resolved generic values) that make up the cycle.
        val modules: List<T>,
        // The specific call-site nodes internal to the cycle - i.e. invocation-graph edges whose
        // caller and callee are both part of it - kept around so a diagnostic can anchor on a real
        // source span instead of just naming functions.
        val callSites: List<ModuleInvocationNode>,
    )

    // Finds cycles in the invocation graph - a function that, directly or indirectly through any
    // number of intermediate calls, invokes itself with the exact same generic interfaces/parameters.
    // Module building never actually recurses forever on this (ModuleInstantiationTracker memoizes
    // by Module.Invocation), so this doesn't surface as a stack overflow while building - it just
    // produces a real cycle in this graph, which would otherwise only be caught later as an uncaught
    // IllegalArgumentException from topologicalSort (used by e.g. hierarchical flattening/retiming,
    // both of which require the invocation graph to be a DAG) instead of a proper diagnostic.
    //
    // A call with a genuinely different parameter each time (e.g. a recursive helper indexed by
    // size - 1) never produces a cycle here at all: each such call targets a distinct Module.Invocation,
    // so the graph just keeps growing along a DAG instead of looping back on itself.
    fun findCycles(): List<Cycle<T>> {
        return graph.stronglyConnectedComponentsTarjan()
            .filter { scc -> scc.size > 1 || graph.edges.any { it.source == it.sink && it.source in scc } }
            .map { scc ->
                Cycle(
                    modules = scc.map { moduleByInvocation[it.value]!! },
                    callSites = graph.edges
                        .filter { it.source in scc && it.sink in scc }
                        .map { it.value },
                )
            }
    }

}