package com.uabutler.netlistir.builder.util

import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualNode
import com.uabutler.netlistir.transformer.util.HierarchicalNetlistLeisersonCircuitConverter
import com.uabutler.netlistir.transformer.util.InvocationGraph
import com.uabutler.util.PropagationDelay

// Node weight is irrelevant to cycle detection - only edge weight (register hop count) matters,
// and that's computed by NetlistLeisersonCircuitConverter regardless of the delay model supplied.
private val zeroDelay = object : PropagationDelay {
    override fun forNode(node: Node) = 0
}

/**
 * Detects purely-combinational feedback loops across the whole program - a node (possibly reached
 * through one or more function calls) whose output eventually drives its own input with no
 * register anywhere along the path. Reuses the same hierarchical graph machinery the compiler's
 * retiming pass builds on ([HierarchicalNetlistLeisersonCircuitConverter]/`HierarchicalLeisersonCircuitGraph`),
 * so a call to a function that itself contains a register correctly breaks the chain instead of
 * being treated as an opaque combinational hop - the false positive a simpler per-module check
 * would produce.
 *
 * Only root modules (functions nothing else in the program calls) are checked: flattening a root's
 * hierarchical graph recursively inlines its entire reachable call tree, so this is both complete
 * (nothing reachable from any root is missed) and avoids reporting the same underlying loop once
 * per level of call nesting. It doesn't fully dedupe across independent roots that happen to share
 * a buggy callee - a rare enough case not worth the extra complexity of cross-root node-identity
 * tracking to eliminate.
 *
 * Returns one entry per independent loop found (the [Node]s involved, sorted by name for
 * deterministic output), empty if there are none.
 */
fun findCombinationalLoops(modules: Collection<MutableModule>): List<List<Node>> {
    val rootModules = InvocationGraph(modules).rootModules().toSet()
    val hierarchicalGraphs = HierarchicalNetlistLeisersonCircuitConverter.fromModules(modules, zeroDelay)
        .filter { it.value in rootModules }

    return hierarchicalGraphs
        .flatMap { graph ->
            val combinationalOnly = graph.flattenToWeightedGraph().graph.subgraph(edgeFilter = { it.weight == 0 })

            combinationalOnly.stronglyConnectedComponentsTarjan()
                .filter { scc ->
                    // The full SCC (including virtual super-input/super-output boundary nodes
                    // introduced by flattening a call site - see HierarchicalNetlistLeisersonCircuitConverter)
                    // is what determines whether this is a genuine cycle; those synthetic nodes
                    // never come from NodeBuilder, so they're stripped from the reported node list
                    // below, but must stay in for the detection check itself.
                    scc.size > 1 || combinationalOnly.edges.any { it.source == it.sink && it.source in scc }
                }
                .map { scc -> scc.map { it.value }.filterNot { it is VirtualNode }.sortedBy { node -> node.name() } }
                .filter { it.isNotEmpty() }
        }
        .sortedBy { loop -> loop.firstOrNull()?.name() ?: "" }
}
