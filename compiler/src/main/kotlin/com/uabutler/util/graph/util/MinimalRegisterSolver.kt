package com.uabutler.util.graph.util

import com.google.ortools.Loader
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import com.uabutler.netlistir.netlist.VirtualNode

class MinimalRegisterSolver<G, N, E>(graph: LeisersonCircuitGraph<G, N, E>): Retiming.Solver<G, N, E>(graph) {
    companion object {
        init { Loader.loadNativeLibraries() }

        fun computeUpperRetimingUpperBound(graph: LeisersonCircuitGraph<*, *, *>, clockPeriod: Int?): Long? = Logger.run("Computing upper bound on retiming label") {
            if (clockPeriod == null) return@run graph.edges.sumOf { it.weight }.toLong()
            return@run FastSolver(graph).solveOrNull(clockPeriod)?.edges?.sumOf { it.weight }?.toLong()
        }
    }

    override fun solveOrNull(targetClockPeriod: Int?): LeisersonCircuitGraph<G, N, E>? = Logger.run("Retiming to minimize register count", Logger.Level.DEBUG) {
        Logger.trace { "Target clock period: $targetClockPeriod" }

        Logger.start("Creating LP problem", Logger.Level.TRACE)

        // Precompute
        Logger.start("Precomputing WD values", Logger.Level.TRACE)

        val pathSequence = graph.nodes.asSequence()
            .flatMap { graph.findFastestConnectionsFromNode(it) }

        val timingConstrainedPaths = if (targetClockPeriod != null) {
            pathSequence.filter { it.delay > targetClockPeriod }.toList()
        } else {
            pathSequence.count() // Force an evalu
            emptyList()
        }

        Logger.finish()

        // Step 1: create the module
        val model = CpModel()

        // Step 2: create the variables
        val upperBound = computeUpperRetimingUpperBound(graph, targetClockPeriod) ?: return@run null
        Logger.trace { "Upper bound on retiming label: $upperBound" }
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

        Logger.trace { "Created objective function with ${objectiveFunctionTerms.size} terms" }

        // Step 4: Add constraints to prevent negative register counts
        val negativeRegisterConstraintCount = graph.edges.onEach { edge ->
            val sourceTerm = LinearExpr.term(retimingLabelVariables[edge.source]!!, -1L)
            val sinkTerm = retimingLabelVariables[edge.sink]!!
            val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

            val bound = -edge.weight.toLong()

            model.addGreaterOrEqual(linearExpression, bound)
        }.count()

        Logger.trace { "Added $negativeRegisterConstraintCount negative register constrains" }

        // Step 5: Add constraints to enforce the clock period constraint
        val clockPeriodConstraintCount = timingConstrainedPaths
            .onEach { connection ->
                val sourceTerm = LinearExpr.term(retimingLabelVariables[connection.source]!!, -1L)
                val sinkTerm = retimingLabelVariables[connection.sink]!!
                val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

                val bound = -connection.registerCount.toLong() + 1

                model.addGreaterOrEqual(linearExpression, bound)
            }.count()

        Logger.trace { "Added $clockPeriodConstraintCount clock period constraints" }

        // Step 6: Add constraints to prevent registers to virtual nodes
        val zeroVirtualNodeRegisterConstraintCount = graph.edges
            .filter { it.source.value is VirtualNode || it.sink.value is VirtualNode }
            .onEach { edge ->
                val sourceTerm = LinearExpr.term(retimingLabelVariables[edge.source]!!, -1L)
                val sinkTerm = retimingLabelVariables[edge.sink]!!
                val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

                val bound = 0L

                model.addEquality(linearExpression, bound)
            }.count()

        Logger.trace { "Added $zeroVirtualNodeRegisterConstraintCount virtual node register constrains" }

        // Step 7: Add an anchor constraint
        val anchorNode = graph.nodes.first()
        val anchorTerm = retimingLabelVariables[anchorNode]!!
        model.addEquality(anchorTerm, 0L)


        Logger.finish() // Creating LP problem

        // Step 7: Run the solver
        val solver = CpSolver()
        val solverStatus = Logger.run("Running LP solver", Logger.Level.TRACE) { solver.solve(model) }

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
            Logger.trace { "${it.value} nodes with lag r(v)=${it.key}" }
        }

        return@run retiming.generateNewCircuit()
    }
}