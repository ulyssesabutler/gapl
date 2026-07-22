package com.uabutler.netlistir.builder.util

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.VirtualNode
import com.uabutler.netlistir.util.graph.HierarchicalNetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.InvocationGraph
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
 * per level of call nesting. This can still independently rediscover the identical textual bug once
 * per generic instantiation that doesn't actually affect it (e.g. round_key(1..10) in aes/test.gapl,
 * or several independent roots that all happen to call the same buggy helper) - that's expected and
 * relied upon, not a gap: the ranking below is specifically deterministic so every such rediscovery
 * produces an equal [com.uabutler.diagnostics.Diagnostic], and DiagnosticsCollector's dedup (by
 * severity+span+kind, which for real distinct problems never collide since their kind's own fields
 * differ) collapses them to one, regardless of which root(s) found them.
 *
 * Returns one entry per independent loop found - the [Node]s involved, ranked most-to-least
 * relevant for a human reader: a name the user actually chose beats one synthesized by
 * AnonymousIdentifierGenerator (e.g. an inlined `+`/`xor` gate, or expanding a generic stdlib
 * helper); among named nodes, a declared node ([BodyNode]) beats a function's own parameter
 * ([com.uabutler.netlistir.netlist.IONode], inherently generic-named since it's reused identically
 * across every call to that function - `i`/`o` and the like); ties break alphabetically by node
 * name, then by owning function name. That last tie-break matters for more than cosmetics: it's
 * what makes the ranking fully content-determined rather than dependent on the arbitrary iteration
 * order of the (identity-based) Set stronglyConnectedComponentsTarjan returns - two structurally
 * identical loops (e.g. the same buggy generic function called once per value of an unrelated
 * generic parameter, as in aes/test.gapl's round_key(1..10)) need to rank their nodes identically
 * so the resulting diagnostics are equal and collapse via DiagnosticsCollector's dedup, instead of
 * surviving as near-duplicates that only differ in node order. Empty if there are no loops.
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
                .map { scc ->
                    scc.map { it.value }
                        .filterNot { it is VirtualNode }
                        .sortedWith(
                            compareBy(
                                { node: Node -> node.name().startsWith("anonymous_") },
                                { node: Node -> node !is BodyNode },
                                { node: Node -> node.name() },
                                { node: Node -> node.parentModule.invocation.gaplFunctionName },
                            )
                        )
                }
                .filter { it.isNotEmpty() }
        }
        .sortedWith(
            compareBy(
                { loop: List<Node> -> loop.firstOrNull()?.name() ?: "" },
                { loop: List<Node> -> loop.firstOrNull()?.parentModule?.invocation?.gaplFunctionName ?: "" },
            )
        )
}
