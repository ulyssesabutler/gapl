package com.uabutler.netlistir.transformer

enum class RetimingSolverKind {
    MONOLITHIC,
    HIERARCHICAL,
}

enum class RetimingSolverId(val id: String, val kind: RetimingSolverKind) {
    FAST("fast", RetimingSolverKind.MONOLITHIC),
    MINIMAL_REGISTER("minimal-register", RetimingSolverKind.MONOLITHIC),
    SCC("scc", RetimingSolverKind.MONOLITHIC),
    HIERARCHICAL_MINIMAL_REGISTER("hierarchical-minimal-register", RetimingSolverKind.HIERARCHICAL);

    companion object {
        fun fromId(id: String): RetimingSolverId =
            entries.firstOrNull { it.id == id } ?: throw Exception("Unknown retiming solver id: $id")
    }
}
