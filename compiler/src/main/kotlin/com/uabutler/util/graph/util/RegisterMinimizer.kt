package com.uabutler.util.graph.util

import com.uabutler.util.graph.LeisersonCircuitGraph
import com.google.ortools.Loader
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.LinearExpr
import kotlin.collections.component1
import kotlin.collections.component2

class RegisterMinimizer<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming<G, N, E>(graph) {

    companion object {

        init { Loader.loadNativeLibraries() }


        fun <G, N, E> minimizeRegisters(graph: LeisersonCircuitGraph<G, N, E>, targetClockPeriod: Int): LeisersonCircuitGraph<G, N, E> = Logger.run("Retiming to minimize register count") {
            Logger.debug { "Target clock period: $targetClockPeriod" }
            Logger.debug { "Initial register count: ${graph.edges.sumOf { it.weight }}" }
            Logger.debug { "Initial clock period: ${graph.computeClockPeriod()}" }

            Logger.start("Creating LP problem")
            // Step 1: create the module
            val model = CpModel()

            // Step 2: create the variables
            val upperBound = graph.edges.sumOf { it.weight }.toLong()
            val retimingLabelVariables = graph.nodes.mapIndexed { index, node ->
                node to model.newIntVar(-upperBound, upperBound, index.toString())
            }.toMap()

            // Step 3: Create the objective function
            val incomingEdges = graph.edges.groupBy { it.sink }
            val outgoingEdges = graph.edges.groupBy { it.source }

            val fanIn = graph.nodes.associateWith { incomingEdges[it]?.size ?: 0 }
            val fanOut = graph.nodes.associateWith { outgoingEdges[it]?.size ?: 0 }

            val nodeCost = graph.nodes.associateWith { fanIn[it]!! - fanOut[it]!! }

            val objectiveFunctionTerms = graph.nodes.map { node ->
                LinearExpr.term(retimingLabelVariables[node]!!, nodeCost[node]!!.toLong())
            }

            val objectiveFunction = LinearExpr.sum(objectiveFunctionTerms.toTypedArray())
            model.minimize(objectiveFunction)

            Logger.debug { "Created objective function with ${objectiveFunctionTerms.size} terms" }

            // Step 4: Add constraints to prevent negative register counts
            val negativeRegisterConstraintCount = graph.edges.onEach { edge ->
                val sourceTerm = LinearExpr.term(retimingLabelVariables[edge.source]!!, -1L)
                val sinkTerm = retimingLabelVariables[edge.sink]!!
                val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

                val bound = -edge.weight.toLong()

                model.addGreaterOrEqual(linearExpression, bound)
            }.count()

            Logger.debug { "Added $negativeRegisterConstraintCount negative register constrains" }

            // Step 5: Add constraints to enforce the clock period constraint
            val clockPeriodConstraintCount = graph.nodes.asSequence()
                .flatMap { graph.findFastestConnectionsFromNode(it) }
                .filter { it.delay > targetClockPeriod }
                .onEach { connection ->
                    val sourceTerm = LinearExpr.term(retimingLabelVariables[connection.source]!!, -1L)
                    val sinkTerm = retimingLabelVariables[connection.sink]!!
                    val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

                    val bound = -connection.registerCount.toLong() + 1

                    model.addGreaterOrEqual(linearExpression, bound)
                }.count()

            Logger.debug { "Added $clockPeriodConstraintCount clock period constraints" }

            // Step 6: Add an anchor constraint
            val anchorNode = graph.nodes.first()
            val anchorTerm = retimingLabelVariables[anchorNode]!!
            model.addEquality(anchorTerm, 0L)

            Logger.finish() // Creating LP problem

            // Step 7: Run the solver
            val solver = CpSolver()
            Logger.run("Running LP solver") { solver.solve(model) }

            // Step 8: Use the retiming values
            val retiming = RegisterMinimizer(graph)

            graph.nodes.forEach { node ->
                val retimingLabel = solver.value(retimingLabelVariables[node]!!)
                retiming.setNodeLag(node, retimingLabel.toInt())
            }

            return@run retiming.generateNewCircuit().also {
                Logger.debug { "Final register count: ${it.edges.sumOf { it.weight }}" }
                Logger.debug { "Final clock period: ${it.computeClockPeriod()}" }

                Logger.run("Final edge weights") {
                    it.edges.groupBy { it.weight }.forEach { (weight, edges) ->
                        Logger.debug { "${edges.size} edges with weight $weight" }
                    }
                }
            }
        }

    }

}