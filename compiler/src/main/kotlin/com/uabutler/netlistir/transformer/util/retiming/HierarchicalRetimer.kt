package com.uabutler.netlistir.transformer.util.retiming

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualBodyNode
import com.uabutler.netlistir.util.graph.HierarchicalNetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.graph.NetlistLeisersonCircuitConverter.NonRegisterConnection
import com.uabutler.netlistir.transformer.util.retiming.solver.HierarchicalMinimalRegisterSolver
import com.uabutler.netlistir.transformer.util.retiming.solver.TimingProperties
import com.uabutler.util.Logger
import com.uabutler.util.PropagationDelay
import com.uabutler.util.graph.LeisersonCircuitGraph

class HierarchicalRetimer(
    val modules: Collection<MutableModule>,
) {

    data class GraphStats(
        val inputDelay: Int?,
        val outputDelay: Int?,
        val combinationalDelay: Int?,
        val registerDelay: Int,
        val clockPeriod: Int,
        val registerCount: Int,
    )

    private val unretimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()
    private val retimedGraphStats: MutableMap<Module.Invocation, GraphStats> = mutableMapOf()

    private fun printGraph(graph: LeisersonCircuitGraph<MutableModule, Node, Collection<NonRegisterConnection>>) = buildString {
        println("PRINTING GRAPH:")
        println("  Nodes:")
        graph.nodes.forEach { node ->
            println("    ${node.weight}: ${node.value.name()} [${node.value.nodeType()}]")
        }
        println("  Edges:")
        graph.edges.forEach { edge ->
            println("    ${edge.weight}: ${edge.source.value.name()} -> ${edge.sink.value.name()}")
        }
    }

    private fun printAllGraphStats(retimeOrder: List<Module.Invocation>) {
        retimeOrder.forEach { invocation ->
            Logger.start("${invocation.gaplFunctionName} retiming analysis", Logger.Level.INFO)

            val unretimedStats = unretimedGraphStats[invocation]
            val retimedStats = retimedGraphStats[invocation]

            if (unretimedStats != null && retimedStats != null) {
                Logger.start("Unretimed", Logger.Level.INFO)
                Logger.info { "Clock Period:   ${unretimedStats.clockPeriod}" }
                Logger.info { "Register Count: ${unretimedStats.registerCount}" }
                Logger.info { "Register Delay: ${unretimedStats.registerDelay}" }
                Logger.finish()

                Logger.start("Retimed", Logger.Level.INFO)
                Logger.info { "Clock Period:   ${retimedStats.clockPeriod}" }
                Logger.info { "Register Count: ${retimedStats.registerCount}" }
                Logger.info { "Register Delay: ${retimedStats.registerDelay}" }
                Logger.finish()

                Logger.info { "Clock Period Decrease:   ${unretimedStats.clockPeriod - retimedStats.clockPeriod}" }
                Logger.info { "Register Count Increase: ${retimedStats.registerCount - unretimedStats.registerCount}" }
                Logger.info { "Register Delay Increase: ${retimedStats.registerDelay - unretimedStats.registerDelay}" }
            } else {
                Logger.error { "No stats for ${invocation.gaplFunctionName}" }
            }

            Logger.finish()
        }
    }

    fun retimeAll(propagationDelay: PropagationDelay, targetClockPeriod: Int): List<MutableModule> {
        val hierarchicalGraphs = HierarchicalNetlistLeisersonCircuitConverter.fromModules(modules, propagationDelay)

        var expansionCounter = 0
        val solveResults = HierarchicalMinimalRegisterSolver(
            graphs = hierarchicalGraphs,
            expansionNodeFactory = {
                VirtualBodyNode(
                    identifier = "expansion-${expansionCounter++}",
                    parentModule = modules.first(),
                ) as Node
            },
            expansionEdgeValueFactory = { emptyList<NonRegisterConnection>() },
        ).solveAll(targetClockPeriod)

        solveResults.forEach { (graph, result) ->
            val invocation = graph.value.invocation
            unretimedGraphStats[invocation] = result.unretimedProperties.toGraphStats()
            retimedGraphStats[invocation] = result.retimedProperties.toGraphStats()
        }

        val retimeOrder = solveResults.keys.map { it.value.invocation }
        val retimedGraphs = solveResults.values.map { it.retimedGraph }

        return HierarchicalNetlistLeisersonCircuitConverter.toModules(retimedGraphs).toList().also {
            printAllGraphStats(retimeOrder)
        }
    }

    private fun TimingProperties.toGraphStats() = GraphStats(
        inputDelay = inputDelay,
        outputDelay = outputDelay,
        combinationalDelay = combinationalDelay,
        registerDelay = registerDelay,
        clockPeriod = clockPeriod,
        registerCount = registerCount,
    )

}