package com.uabutler.diagnostics

import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer

class ParserErrorListener(private val collector: DiagnosticsCollector) : BaseErrorListener() {

    override fun syntaxError(
        recognizer: Recognizer<*, *>,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        collector.reportError(
            kind = SyntaxDiagnosticKind.SyntaxError(msg),
            span = SourceSpan(line, charPositionInLine, line, charPositionInLine),
        )
    }

}
