package com.uabutler.vcd

/**
 * Generates VCD's compact per-signal identifier codes: base-94 positional strings over printable
 * ASCII 33 ('!')..126 ('~'), most-significant digit first. index 0 -> "!", index 93 -> "~",
 * index 94 -> "\"!" (two chars), etc. Does not need to be "bijective base-94" or match any
 * particular external tool's numbering — only a unique string per distinct non-negative index.
 */
object IdentifierCodes {
    private const val FIRST_CHAR_CODE = 33 // '!'
    private const val RADIX = 94 // 126 - 33 + 1, i.e. '~' - '!' + 1

    fun forIndex(index: Int): String {
        require(index >= 0) { "IdentifierCodes.forIndex requires a non-negative index, got $index" }
        val digits = mutableListOf<Char>()
        var n = index
        do {
            digits += (FIRST_CHAR_CODE + n % RADIX).toChar()
            n /= RADIX
        } while (n > 0)
        return digits.reversed().joinToString("")
    }
}
