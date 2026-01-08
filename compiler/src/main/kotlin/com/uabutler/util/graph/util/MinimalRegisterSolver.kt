package com.uabutler.util.graph.util

import com.google.ortools.Loader
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr

class MinimalRegisterSolver<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming.Solver<G, N, E>(graph) {
    companion object { init { Loader.loadNativeLibraries() }}

    override fun solveOrNull(targetClockPeriod: Int?): LeisersonCircuitGraph<G, N, E>? = Logger.run("Retiming to minimize register count") {
        Logger.debug { "Target clock period: $targetClockPeriod" }

        Logger.start("Creating LP problem")

        // Precompute
        Logger.start("Precomputing WD values")
        var longestRegisterPath = 0

        val pathSequence = graph.nodes.asSequence()
            .flatMap { graph.findFastestConnectionsFromNode(it) }
            .onEach { longestRegisterPath = maxOf(longestRegisterPath, it.registerCount) }

        val timingConstrainedPaths = if (targetClockPeriod != null) {
            pathSequence.filter { it.delay > targetClockPeriod }.toList()
        } else {
            emptyList()
        }

        Logger.finish()

        // Step 1: create the module
        val model = CpModel()

        // Step 2: create the variables
        val upperBound = longestRegisterPath.toLong()
        Logger.debug { "Upper bound on retiming label: $upperBound" }
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
        val clockPeriodConstraintCount = timingConstrainedPaths
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
        val solverStatus = Logger.run("Running LP solver") { solver.solve(model) }

        when (solverStatus) {
            CpSolverStatus.OPTIMAL -> Logger.debug { "LP solver found optimal solution" }
            else -> {
                Logger.debug { "LP solver did not find an optimal solution" }
                return@run null
            }
        }

        // Step 8: Use the retiming values
        val retiming = Retiming(graph)

        graph.nodes.map { node ->
            val retimingLabel = solver.value(retimingLabelVariables[node]!!)
            retiming.setNodeLag(node, retimingLabel.toInt())
            retimingLabel
        }.groupBy { it }.mapValues { (_, value) -> value.size }.forEach {
            Logger.debug { "${it.value} nodes with lag r(v)=${it.key}" }
        }

        return@run retiming.generateNewCircuit()
    }
}