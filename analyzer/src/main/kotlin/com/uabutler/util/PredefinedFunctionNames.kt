package com.uabutler.util

enum class PredefinedFunctionNames(val gaplName: String) {
    LESS_THAN("less_than"),
    GREATER_THAN("greater_than"),
    LESS_THAN_EQUALS("less_than_equals"),
    GREATER_THAN_EQUALS("greater_than_equals"),
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    AND("and"),
    OR("or"),
    NOT("not"),
    BITWISE_AND("bitwise_and"),
    BITWISE_OR("bitwise_or"),
    BITWISE_XOR("bitwise_xor"),
    BITWISE_NOT("bitwise_not"),
    ADD("add"),
    SUBTRACT("subtract"),
    MULTIPLY("multiply"),
    LEFT_SHIFT("left_shift"),
    RIGHT_SHIFT("right_shift"),
    REGISTER("register"),
    INTEGER_REGISTER("integer_register"),
    LITERAL("literal"),
    MUX("mux"),
    DEMUX("demux"),
    PRIORITY("priority"),

    // View conversions. Unlike the primitives above these never become netlist nodes of their own:
    // NodeBuilder lowers each one to plain wiring between pass-through nodes.
    PROJECT("project"),
    OVERLAY("overlay"),
    ZEROS("zeros"),

    // Explicit forks for @ready_valid bodies, where an implicit fork is an error (type-system.md §5).
    // Also lowered to wiring by NodeBuilder; the handshake each one implies is stage 2c's work.
    FORK_EAGER("fork_eager"),
    FORK_LAZY("fork_lazy"),
    ;

    companion object {
        fun from(name: String): PredefinedFunctionNames? =
            entries.firstOrNull { it.gaplName == name }
    }
}