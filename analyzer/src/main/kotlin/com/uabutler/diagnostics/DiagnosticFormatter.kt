package com.uabutler.diagnostics

object DiagnosticFormatter {

    fun format(diagnostic: Diagnostic, source: String): String = buildString {
        appendLine(diagnostic.toString())

        val lines = source.lines()
        val lineIndex = diagnostic.span.startLine - 1
        if (lineIndex !in lines.indices) return@buildString

        val lineNumberLabel = "${diagnostic.span.startLine} | "
        appendLine("$lineNumberLabel${lines[lineIndex]}")
        append(" ".repeat(lineNumberLabel.length + diagnostic.span.startColumn))
        append("^")
    }

}
