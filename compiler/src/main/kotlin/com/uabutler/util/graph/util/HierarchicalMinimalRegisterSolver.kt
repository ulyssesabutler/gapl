package com.uabutler.util.graph.util

import com.google.ortools.Loader
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import com.uabutler.netlistir.netlist.VirtualIONode
import com.uabutler.util.graph.HierarchicalLeisersonCircuitGraph
import com.uabutler.util.graph.WeightedGraph

class HierarchicalMinimalRegisterSolver<G, N, E>(override val graph: HierarchicalLeisersonCircuitGraph<G, N, E>): Retiming.Solver<G, N, E>(graph) {
    companion object {
        init { Loader.loadNativeLibraries() }

        fun computeUpperRetimingUpperBound(graph: HierarchicalLeisersonCircuitGraph<*, *, *>, clockPeriod: Int?): Long? = Logger.run("Computing upper bound on retiming label") {
            if (clockPeriod == null) return@run graph.edges.sumOf { it.weight }.toLong()
            return@run FastSolver(graph).solveOrNull(clockPeriod)?.edges?.sumOf { it.weight }?.toLong()
        }
    }

    override fun solveOrNull(targetClockPeriod: Int?): HierarchicalLeisersonCircuitGraph<G, N, E>? = Logger.run("Retiming to minimize register count", Logger.Level.DEBUG) {
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
        val upperBound = (computeUpperRetimingUpperBound(graph, targetClockPeriod) ?: return@run null) + 1
        Logger.debug { "Upper bound on retiming label: $upperBound" }
        val retimingLabelVariables = graph.nodes.mapIndexed { index, node ->
            node to model.newIntVar(-upperBound, upperBound, "v$index-${node.value}")
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

        // Step 6: Add constraints to prevent registers to virtual IO nodes
        val zeroVirtualNodeRegisterConstraintCount = graph.edges
            .filter { it.source.value is VirtualIONode || it.sink.value is VirtualIONode }
            .onEach { edge ->
                val sourceTerm = LinearExpr.term(retimingLabelVariables[edge.source]!!, -1L)
                val sinkTerm = retimingLabelVariables[edge.sink]!!
                val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

                val bound = 0L

                model.addEquality(linearExpression, bound)
            }.count()

        Logger.trace { "Added $zeroVirtualNodeRegisterConstraintCount virtual node register constrains" }

        // Step 7: Add constraints to require the contract circuits to account for contracted registers
        val contractedRegisterConstraintCount = graph.contractCircuitGraphs.onEach { contractCircuitGraph ->
            val sourceTerm = LinearExpr.term(retimingLabelVariables[contractCircuitGraph.inputNode]!!, -1L)
            val sinkTerm = retimingLabelVariables[contractCircuitGraph.outputNode]!!
            val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())

            val value = (contractCircuitGraph.retimedRegisterDelay - contractCircuitGraph.unretimedRegisterDelay).toLong()
            model.addEquality(linearExpression, value)
        }.count()

        Logger.trace { "Added $contractedRegisterConstraintCount contract circuit constrains" }

        // Step 8: Add an anchor constraint
        val anchorNode = graph.nodes.first()
        val anchorTerm = retimingLabelVariables[anchorNode]!!
        model.addEquality(anchorTerm, 0L)

        Logger.finish() // Creating LP problem

        // LP export for debugging
        Logger.trace {
            fun varName(node: WeightedGraph.Node<N>) = retimingLabelVariables[node]!!.name
            fun formatTerm(coeff: Int, name: String) = if (coeff >= 0) "+ $coeff $name" else "- ${-coeff} $name"

            val sb = StringBuilder()

            // Objective
            val objTerms = graph.nodes.mapNotNull { node ->
                val coeff = nodeCost[node]!!
                if (coeff == 0) null else formatTerm(coeff, varName(node))
            }
            sb.appendLine("Minimize")
            sb.appendLine("  obj: ${objTerms.joinToString(" ").removePrefix("+ ")}")
            sb.appendLine()

            sb.appendLine("Subject To")

            graph.edges.forEachIndexed { i, edge ->
                sb.appendLine("  neg_$i: ${varName(edge.sink)} - ${varName(edge.source)} >= ${-edge.weight}")
            }

            timingConstrainedPaths.forEachIndexed { i, conn ->
                sb.appendLine("  clk_$i: ${varName(conn.sink)} - ${varName(conn.source)} >= ${-conn.registerCount + 1}")
            }

            graph.edges
                .filter { it.source.value is VirtualIONode || it.sink.value is VirtualIONode }
                .forEachIndexed { i, edge ->
                    sb.appendLine("  virt_$i: ${varName(edge.sink)} - ${varName(edge.source)} = 0")
                }

            graph.contractCircuitGraphs.forEachIndexed { i, ccg ->
                val value = ccg.retimedRegisterDelay - ccg.unretimedRegisterDelay
                sb.appendLine("  contract_$i: ${varName(ccg.outputNode)} - ${varName(ccg.inputNode)} = $value")
            }

            sb.appendLine("  anchor: ${varName(anchorNode)} = 0")

            sb.appendLine()
            sb.appendLine("Bounds")
            graph.nodes.forEach { node -> sb.appendLine("  -$upperBound <= ${varName(node)} <= $upperBound") }

            sb.appendLine()
            sb.appendLine("Generals")
            sb.appendLine("  ${graph.nodes.joinToString(" ") { varName(it) }}")
            sb.appendLine()
            sb.append("End")

            sb.toString()
        }

        // Step 9: Run the solver
        val solver = CpSolver()
        val solverStatus = Logger.run("Running LP solver", Logger.Level.DEBUG) { solver.solve(model) }

        when (solverStatus) {
            CpSolverStatus.OPTIMAL -> Logger.debug { "LP solver found optimal solution" }
            else -> {
                Logger.debug { "LP solver did not find an optimal solution: $solverStatus" }
                return@run null
            }
        }

        // Step 8: Use the retiming values
        val retiming = Retiming(
            graph = graph,
            graphFactory = { nodes, edges -> HierarchicalLeisersonCircuitGraph(graph.value, nodes, edges, graph.contractCircuitGraphs) }
        )

        graph.nodes.map { node ->
            val retimingLabel = solver.value(retimingLabelVariables[node]!!)
            retiming.setNodeLag(node, retimingLabel.toInt())
            retimingLabel
        }.groupBy { it }.mapValues { (_, value) -> value.size }.forEach {
            Logger.trace { "${it.value} nodes with lag r(v)=${it.key}" }
        }

        val initialUpdatedCircuit = retiming.generateNewCircuit() as HierarchicalLeisersonCircuitGraph<G, N, E>
        val actualEdgeValues = initialUpdatedCircuit.contractCircuitGraphs.associate { it.edge to it.retimedRegisterDelay }
        val revertedEdges = initialUpdatedCircuit.edges.map { if (it in actualEdgeValues) { it.copy(weight = actualEdgeValues[it]!!) } else { it } }

        return@run HierarchicalLeisersonCircuitGraph(graph.value, graph.nodes, revertedEdges, graph.contractCircuitGraphs)
    }
}