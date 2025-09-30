package com.uabutler.util.graph

fun String.dotEsc(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

fun <N, E> WeightedGraph<N, E>.toDot(
    graphName: String,
    rankdir: String? = "LR",
    edgeName: (WeightedGraph.Edge<N, E>) -> String = { "" },
    nodeName: (WeightedGraph.Node<N>) -> String = { it.value.toString() },
    edgeIsFeedback: (WeightedGraph.Edge<N, E>) -> Boolean = { false },
): String {
    val id = nodes.withIndex().associate { (i, n) -> n to "n$i" }

    fun nodeFinalLabel(n: WeightedGraph.Node<N>): String {
        val base = nodeName(n).trim()
        val full = if (base.isEmpty()) "${n.weight}" else "$base [${n.weight}]"
        return full.dotEsc()
    }
    fun edgeFinalLabel(e: WeightedGraph.Edge<N, E>): String {
        val base = edgeName(e).trim()
        val full = if (base.isEmpty()) "${e.weight}" else "$base [${e.weight}]"
        return full.dotEsc()
    }

    return buildString {
        appendLine("digraph \"${graphName.dotEsc()}\" {")
        if (rankdir != null) appendLine("  rankdir=$rankdir;")
        appendLine("  splines=true;")
        appendLine("  node [shape=box, fontname=\"JetBrains Mono,Monospace\"];")
        appendLine("  edge [fontname=\"JetBrains Mono,Monospace\"];")

        // Nodes
        for (n in nodes) {
            appendLine("""  ${id[n]} [label="${nodeFinalLabel(n)}"];""")
        }

        // Edges
        for (e in edges) {
            val pen = (1 + e.weight.coerceAtLeast(0)).coerceAtMost(6)
            val attrs = mutableListOf(
                """label="${edgeFinalLabel(e)}"""",
                "penwidth=$pen"
            )
            if (edgeIsFeedback(e)) attrs += "constraint=false"
            appendLine("  ${id[e.source]} -> ${id[e.sink]} [${attrs.joinToString(", ")}];")
        }

        appendLine("}")
    }
}
