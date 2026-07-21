package com.uabutler.diagnostics

import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.Token

data class SourceSpan(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
) {
    override fun toString() = if (startLine == endLine) {
        "$startLine:$startColumn-$endColumn"
    } else {
        "$startLine:$startColumn-$endLine:$endColumn"
    }

    fun shiftedLines(delta: Int) = copy(startLine = startLine + delta, endLine = endLine + delta)

    companion object {
        fun of(token: Token) = SourceSpan(
            startLine = token.line,
            startColumn = token.charPositionInLine,
            endLine = token.line,
            endColumn = token.charPositionInLine + (token.text?.length ?: 0),
        )

        fun of(start: Token, stop: Token) = SourceSpan(
            startLine = start.line,
            startColumn = start.charPositionInLine,
            endLine = stop.line,
            endColumn = stop.charPositionInLine + (stop.text?.length ?: 0),
        )

        fun of(context: ParserRuleContext) = of(context.start!!, context.stop ?: context.start!!)
    }
}
