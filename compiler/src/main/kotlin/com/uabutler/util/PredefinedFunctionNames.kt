package com.uabutler.util

enum class PredefinedFunctionNames(val gaplName: String) {
    LESS_THAN_EQUALS("less_than_equals"),
    GREATER_THAN_EQUALS("greater_than_equals"),
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    AND("and"),
    OR("or"),
    BITWISE_AND("bitwise_and"),
    BITWISE_OR("bitwise_or"),
    BITWISE_XOR("bitwise_xor"),
    ADD("add"),
    SUBTRACT("subtract"),
    MULTIPLY("multiply"),
    LEFT_SHIFT("left_shift"),
    RIGHT_SHIFT("right_shift"),
    REGISTER("register"),
    LITERAL("literal"),
    MUX("mux"),
    DEMUX("demux"),
    ;

    companion object {
        fun from(name: String): PredefinedFunctionNames? =
            entries.firstOrNull { it.gaplName == name }
    }
}