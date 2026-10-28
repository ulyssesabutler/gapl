package com.uabutler.util

/**
 * Layer-3 protocols (brainstorming/claude/type-system.md §5), declared in the order of what they
 * tolerate: @pure (no gaps, no stalls) < @valid (gaps) < @ready_valid (gaps and stalls). A call is
 * legal exactly when the callee's protocol is at or below the caller's, so `compareTo` is the coercion
 * matrix.
 */
enum class Protocol(val gaplName: String) {
    PURE("pure"),
    VALID("valid"),
    READY_VALID("ready_valid"),
    ;

    override fun toString() = "@$gaplName"

    companion object {
        fun from(name: String): Protocol? = entries.firstOrNull { it.gaplName == name }
    }
}
