package com.uabutler.verilogir.module.statement.util

enum class BinaryOperator(val verilog: String) {
    // Arithmetic
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MOD("%"),
    POWER("**"),

    // Relational
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_THAN_EQUALS("<="),
    GREATER_THAN_EQUALS(">="),

    // Equality
    EQUALS("=="),
    NOT_EQUALS("!="),

    // Logical
    AND("&&"),
    OR("||"),

    // Bitwise
    BITWISE_AND("&"),
    BITWISE_OR("|"),

    // Shift
    LEFT_SHIFT("<<"),
    RIGHT_SHIFT(">>"),
    ARITHMETIC_LEFT_SHIFT("<<<"),
    ARITHMETIC_RIGHT_SHIFT(">>>"),
}