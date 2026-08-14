package com.uabutler.netlistir.transformer.util.retiming.solver

import com.google.ortools.Loader
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.Logger
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualIONode
import com.uabutler.netlistir.transformer.util.retiming.MonolithicRetimingProblem
import com.uabutler.netlistir.util.graph.NetlistLeisersonCircuitConverter
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
    /**
     * The individual driven bits an edge carries, identified so that two edges out of the same node
     * report the *same* object for a bit they both carry. That identity is what lets the objective
     * charge a shared shift register once instead of once per consumer, and the count is what lets it
     * charge a 512-bit bus 512 times what it charges a 1-bit wire.
     *
     * The default hands every edge one anonymous bit of its own, which reproduces the classic
     * sum-of-edge-weights objective exactly - correct for any graph with no netlist wires behind it
     * (the unit tests) and for the synthetic boundary edges hierarchical solvers add, which emit no
     * hardware and so should cost nothing to weight by width.
     */
    private val edgeSourceBits: (WeightedGraph.Edge<N, E>) -> Collection<Any> = { listOf(Any()) },
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

        /**
         * [edgeSourceBits] for a graph built from a netlist: the driving wires behind the edge, one
         * per bit. Two edges out of the same node report the same `OutputWire` object for a bit they
         * both carry (netlist [com.uabutler.netlistir.netlist.Wire] has no `equals` override, so this
         * is identity), which is what makes shared fanout visible to the objective.
         *
         * Synthetic edges - super-source/sink, and the boundary edges hierarchical solvers add for an
         * already-solved child - carry no connections and so report no bits. That is deliberate: no
         * hardware is emitted for them, so they should not be priced.
         */
        fun netlistEdgeSourceBits(
            edge: WeightedGraph.Edge<Node, Collection<NetlistLeisersonCircuitConverter.NonRegisterConnection>>,
        ): Collection<Any> = edge.value.map { it.source }

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

        // The cheap bound is derived from FastSolver's register count. It is only a heuristic, in two
        // separate ways, and neither of them is allowed to be the last word on feasibility:
        //
        //  - It is not a proof that an optimal solution fits inside +/-bound. The binding direction
        //    is the lower one (r(v) - r(u) >= -sum of w along a path), and FEAS can strictly *reduce*
        //    total register count, so the box can exclude every feasible point.
        //  - FastSolver failing outright does not mean the model is infeasible. It solves a
        //    *relaxation* - it ignores additionalEqualityConstraints entirely - and it assumes
        //    non-negative starting edge weights, which contracted-subgraph edges deliberately
        //    violate. On a per-port hierarchical graph those weights are negative routinely, so
        //    FEAS's own correctness argument simply does not apply.
        //
        // So: try the cheap box when there is one, then fall through to a bound that is actually
        // provable before reporting infeasible.
        val heuristicBound = computeUpperRetimingUpperBound(graph, targetClockPeriod)?.plus(1)
        if (heuristicBound != null) {
            solveWithLabelBound(heuristicBound, timingConstrainedPaths)?.let { return@run it }
        }

        val provableBound = provableLabelBound(timingConstrainedPaths)
        if (heuristicBound != null && provableBound <= heuristicBound) return@run null

        Logger.debug {
            "Retrying at provable retiming-label bound $provableBound " +
                "(heuristic bound ${heuristicBound ?: "unavailable - FastSolver found no feasible relaxation"})"
        }
        return@run solveWithLabelBound(provableBound, timingConstrainedPaths)
    }

    /**
     * A bound on |r(v)| wide enough that the model is feasible inside it whenever it is feasible at
     * all, unlike [computeUpperRetimingUpperBound].
     *
     * Every constraint on r is a difference constraint r(sink) - r(source) >= b (each equality being
     * two of them), so the feasible region for r is a difference-constraint polyhedron, and with
     * r(anchor) pinned to 0 every vertex of it satisfies |r(v)| <= (|V| - 1) * max|b| - the
     * Bellman-Ford longest-path bound on the constraint graph. A non-empty polyhedron has a vertex,
     * so a box this wide can only come up empty if the model is genuinely infeasible. That is exactly
     * what this is used for: distinguishing "infeasible" from "the cheap box was too small".
     *
     * It is *not* a guarantee that a true optimum lies inside. The shared-fanout depth variables make
     * the objective a sum of maxima rather than a plain linear function of r, so the vertex argument
     * that used to carry optimality over no longer applies. In practice this only affects the
     * fallback path, where returning a feasible retiming beats reporting a false infeasibility.
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

    /**
     * Groups the graph's driven bits by the exact set of edges they ride, as (edges, bit count).
     *
     * Bits that ride the same set of edges always end up at the same shift-register depth, so one
     * term per group is enough. Edges reporting no bits at all (a hierarchical solver's synthetic
     * boundary edges, the super-source/sink edges) appear in no group and so cost nothing, which is
     * right: no hardware is emitted for them.
     */
    private fun objectiveBitGroups(): List<Pair<List<WeightedGraph.Edge<N, E>>, Int>> {
        val edgeList = graph.edges.toList()

        // Identity-keyed and insertion-ordered. A bit is a netlist wire, which has no equals
        // override, so a LinkedHashMap is already identity-keyed - and unlike an IdentityHashMap it
        // iterates deterministically, which matters because this order feeds CP-SAT's variable order.
        val edgesPerBit = LinkedHashMap<Any, MutableList<Int>>()
        edgeList.forEachIndexed { index, edge ->
            edgeSourceBits(edge).forEach { bit ->
                edgesPerBit.getOrPut(bit) { mutableListOf() }.add(index)
            }
        }

        val groups = LinkedHashMap<List<Int>, Int>()
        edgesPerBit.values.forEach { edgeIndices ->
            val key = edgeIndices.distinct().sorted()
            groups[key] = (groups[key] ?: 0) + 1
        }

        return groups.map { (edgeIndices, bitCount) -> edgeIndices.map { edgeList[it] } to bitCount }
    }

    /** The largest depth any shared shift register could need, given a bound on |r(v)|. */
    private fun sharedDepthUpperBound(labelBound: Long): Long {
        val maxEdgeWeight = graph.edges.maxOfOrNull { kotlin.math.abs(it.weight.toLong()) } ?: 0L
        return maxEdgeWeight + 2L * labelBound
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

        // Step 3: Create the objective function.
        //
        // NetlistLeisersonCircuitConverter.addSharedWeightedConnections materialises one shift
        // register per driving bit, as deep as that bit's most-delayed consumer, so the cost of a
        // retiming is
        //
        //     sum over driving bits b of  max over edges e carrying b of w_r(e)
        //
        // and *not* the sum of edge weights. Two things follow. Bits are counted individually, so a
        // register on a 512-bit bus costs 512 where one on a 1-bit wire costs 1 - the old objective
        // priced them identically, which made "minimal register count" mean something quite far from
        // minimal flip-flops. And fanout is shared, so a bit feeding three consumers at the same
        // depth is charged once rather than three times.
        //
        // Bits carrying the identical set of outgoing edges necessarily take the identical depth, so
        // they collapse into one term weighted by how many bits it stands for.
        val bitGroups = objectiveBitGroups()

        // A group riding a single edge needs no auxiliary variable: w_r(e) >= 0 is already enforced
        // below, so its depth *is* w_r(e), and the term folds into the per-node coefficients the same
        // way the old fanIn-minus-fanOut objective did. Every group is a singleton whenever
        // edgeSourceBits is left at its default, so this is also what keeps the model the same size
        // as before for callers that have no widths.
        val nodeCost = mutableMapOf<WeightedGraph.Node<N>, Long>()
        val sharedDepthTerms = mutableListOf<LinearExpr>()

        bitGroups.forEach { (edges, bitCount) ->
            val weight = bitCount.toLong()
            if (edges.size == 1) {
                val edge = edges.single()
                nodeCost[edge.sink] = (nodeCost[edge.sink] ?: 0L) + weight
                nodeCost[edge.source] = (nodeCost[edge.source] ?: 0L) - weight
            } else {
                val depth = model.newIntVar(0L, sharedDepthUpperBound(upperBound), "depth${sharedDepthTerms.size}")
                edges.forEach { edge ->
                    // depth >= w(e) + r(sink) - r(source)
                    model.addGreaterOrEqual(
                        LinearExpr.sum(
                            arrayOf(
                                depth,
                                LinearExpr.term(retimingLabelVariables[edge.sink]!!, -1L),
                                retimingLabelVariables[edge.source]!!,
                            )
                        ),
                        edge.weight.toLong(),
                    )
                }
                sharedDepthTerms.add(LinearExpr.term(depth, weight))
            }
        }

        val objectiveFunctionTerms = nodeCost.filterValues { it != 0L }.map { (node, cost) ->
            LinearExpr.term(retimingLabelVariables[node]!!, cost)
        } + sharedDepthTerms

        val objectiveFunction = LinearExpr.sum(objectiveFunctionTerms.toTypedArray())
        model.minimize(objectiveFunction)

        Logger.trace {
            "Created objective function with ${objectiveFunctionTerms.size} terms " +
                "(${sharedDepthTerms.size} shared-fanout depth variables)"
        }

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
            fun formatTerm(coeff: Long, name: String) = if (coeff >= 0) "+ $coeff $name" else "- ${-coeff} $name"

            val sb = StringBuilder()

            // Objective. The shared-fanout depth variables are omitted here - this dump exists to
            // eyeball the retiming constraints, and those are unaffected by them.
            val objTerms = graph.nodes.mapNotNull { node ->
                val coeff = nodeCost[node] ?: 0L
                if (coeff == 0L) null else formatTerm(coeff, varName(node))
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