package com.uabutler.v2.verilogir.module.statement.util

enum class UnaryOperator(val verilog: String) {
    // Logical
    NOT("!"),

    // Bitwise
    BITWISE_NOT("~"),
}