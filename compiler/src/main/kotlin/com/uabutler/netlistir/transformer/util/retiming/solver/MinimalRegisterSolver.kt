package com.uabutler.netlistir.transformer.util.retiming.solver

import com.google.ortools.Loader
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import com.uabutler.netlistir.netlist.VirtualIONode
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.transformer.util.retiming.Retiming
import com.uabutler.util.graph.WeightedGraph

data class NodeEqualityConstraint<N>(
    val source: WeightedGraph.Node<N>,
    val sink: WeightedGraph.Node<N>,
    val value: Long,
)

class MinimalRegisterSolver<G, N, E>(
    problem: MonolithicRetimingProblem<G, N, E>,
    private val additionalEqualityConstraints: List<NodeEqualityConstraint<N>> = emptyList(),
): MonolithicSolver<G, N, E>(problem) {

    private val graph = problem.graph

    /**
     * The retiming labels r(v) chosen by the most recent successful [solveOrNull], or null if no
     * solve has succeeded on this instance.
     *
     * Exposed because a retiming is fully described by its labels, and several things a caller
     * legitimately needs cannot be recovered from the retimed graph alone. In particular
     * HierarchicalMinimalRegisterSolver needs r(super-out) - r(super-in) - the component retiming
     * difference it pins its parent against - which is *not* the same as the change in the graph's
     * minimum input-to-output register count whenever the graph has more than one graph-theoretic
     * leaf. It also uses these labels to recompute retimed edge weights directly (w + r(sink) -
     * r(source)) rather than matching retimed edges back to originals positionally.
     */
    var lastSolveNodeLags: Map<WeightedGraph.Node<N>, Int>? = null
        private set

    companion object {
        init { Loader.loadNativeLibraries() }

        // Sums of *absolute* edge weight, not signed weight: HierarchicalMinimalRegisterSolver's
        // per-child "expansion" edges are deliberately built with negative starting weight (see its
        // own comments) to encode an already-solved child's boundary offset via an accompanying
        // equality constraint, not a real register count - so a signed sum here can come out
        // negative for those flat graphs. That previously produced a negative upperBound, handing
        // model.newIntVar an inverted (empty) domain and making CP-SAT report MODEL_INVALID, which
        // solveOrNull's caller then couldn't distinguish from a genuinely infeasible clock period.
        // FastSolver also isn't a safe source of non-negative weights here: it's a pure heuristic
        // that only checks the resulting clock period, so it never re-establishes "all final edge
        // weights are non-negative" for a graph that didn't already start that way.
        fun computeUpperRetimingUpperBound(graph: LeisersonCircuitGraph<*, *, *>, clockPeriod: Int?): Long? = Logger.run("Computing upper bound on retiming label") {
            if (clockPeriod == null) return@run graph.edges.sumOf { kotlin.math.abs(it.weight.toLong()) }
            return@run FastSolver(MonolithicRetimingProblem(graph)).solveOrNull(clockPeriod)?.graph?.edges?.sumOf { kotlin.math.abs(it.weight.toLong()) }
        }
    }

    override fun solveOrNull(targetClockPeriod: Int?): MonolithicRetimingProblem<G, N, E>? = Logger.run("Retiming to minimize register count", Logger.Level.DEBUG) {
        Logger.trace { "Target clock period: $targetClockPeriod" }

        lastSolveNodeLags = null

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

        val heuristicBound = (computeUpperRetimingUpperBound(graph, targetClockPeriod) ?: return@run null) + 1
        solveWithLabelBound(heuristicBound, timingConstrainedPaths)?.let { return@run it }

        // The heuristic bound above is derived from FastSolver's register count, which is not a
        // proof that an optimal solution fits inside +/-heuristicBound: the binding direction is the
        // lower one (r(v) - r(u) >= -sum of w along a path), and FEAS can strictly *reduce* total
        // register count, so the box can in principle exclude every feasible point and make CP-SAT
        // report infeasible for an achievable clock period. Retry once against a bound that is
        // actually provable before believing "infeasible". Only reachable when FastSolver already
        // found a feasible retiming, so it does not slow down the common infeasible-probe path
        // through findMinimumClockPeriod.
        val provableBound = provableLabelBound(timingConstrainedPaths)
        if (provableBound <= heuristicBound) return@run null

        Logger.debug {
            "Infeasible at heuristic retiming-label bound $heuristicBound; retrying at provable bound $provableBound"
        }
        return@run solveWithLabelBound(provableBound, timingConstrainedPaths)
    }

    /**
     * A bound on |r(v)| that holds for some optimal solution, unlike [computeUpperRetimingUpperBound].
     *
     * Every constraint emitted below is a difference constraint r(sink) - r(source) >= b (each
     * equality being two of them), so the feasible region is a difference-constraint polyhedron.
     * The objective is linear, so an optimum is attained at a vertex, and with r(anchor) pinned to 0
     * every vertex satisfies |r(v)| <= (|V| - 1) * max|b| - the Bellman-Ford longest-path bound on
     * the constraint graph. Loose, but sound.
     */
    private fun provableLabelBound(
        timingConstrainedPaths: List<LeisersonCircuitGraph.FastestConnection<N>>,
    ): Long {
        val maxRightHandSide = maxOf(
            graph.edges.maxOfOrNull { kotlin.math.abs(it.weight.toLong()) } ?: 0L,
            timingConstrainedPaths.maxOfOrNull { kotlin.math.abs(1L - it.registerCount) } ?: 0L,
            additionalEqualityConstraints.maxOfOrNull { kotlin.math.abs(it.value) } ?: 0L,
        ).coerceAtLeast(1L)

        return (graph.nodes.size.toLong() - 1).coerceAtLeast(1L) * maxRightHandSide
    }

    private fun solveWithLabelBound(
        upperBound: Long,
        timingConstrainedPaths: List<LeisersonCircuitGraph.FastestConnection<N>>,
    ): MonolithicRetimingProblem<G, N, E>? = Logger.run("Solving at retiming-label bound $upperBound", Logger.Level.DEBUG) {
        Logger.start("Creating LP problem", Logger.Level.TRACE)

        // Step 1: create the module
        val model = CpModel()

        // Step 2: create the variables
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

        // Step 6: Add constraints to prevent registers to virtual nodes
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

        // Step 7: Add additional equality constraints (r(sink) - r(source) = value)
        val additionalConstraintCount = additionalEqualityConstraints.onEach { constraint ->
            val sourceTerm = LinearExpr.term(retimingLabelVariables[constraint.source]!!, -1L)
            val sinkTerm = retimingLabelVariables[constraint.sink]!!
            val linearExpression = LinearExpr.sum(listOf(sourceTerm, sinkTerm).toTypedArray())
            model.addEquality(linearExpression, constraint.value)
        }.count()

        Logger.trace { "Added $additionalConstraintCount additional equality constraints" }

        // Step 8: Add an anchor constraint
        val anchorNode = graph.nodes.first()
        val anchorTerm = retimingLabelVariables[anchorNode]!!
        model.addEquality(anchorTerm, 0L)

        Logger.finish() // Creating LP problem

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

            additionalEqualityConstraints.forEachIndexed { i, constraint ->
                sb.appendLine("  extra_$i: ${varName(constraint.sink)} - ${varName(constraint.source)} = ${constraint.value}")
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
        val solverStatus = Logger.run("Running LP solver", Logger.Level.TRACE) { solver.solve(model) }

        when (solverStatus) {
            CpSolverStatus.OPTIMAL -> Logger.debug { "LP solver found optimal solution" }
            else -> {
                Logger.debug { "LP solver did not find an optimal solution: $solverStatus; validate=${model.validate()}" }
                return@run null
            }
        }

        // Step 10: Use the retiming values
        val retiming = Retiming(
            graph = graph,
            graphFactory = { nodes, edges -> LeisersonCircuitGraph(graph.value, nodes, edges) }
        )

        val nodeLags = graph.nodes.associateWith { node ->
            solver.value(retimingLabelVariables[node]!!).toInt()
        }

        nodeLags.values.groupingBy { it }.eachCount().forEach { (lag, count) ->
            Logger.trace { "$count nodes with lag r(v)=$lag" }
        }

        nodeLags.forEach { (node, lag) -> retiming.setNodeLag(node, lag) }
        lastSolveNodeLags = nodeLags

        return@run MonolithicRetimingProblem(retiming.generateNewCircuit())
    }
}