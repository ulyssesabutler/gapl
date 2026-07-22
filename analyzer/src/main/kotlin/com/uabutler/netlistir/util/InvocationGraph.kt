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

}