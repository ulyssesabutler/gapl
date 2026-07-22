package diagnostics

import com.uabutler.Analyzer
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsException

object AnalyzerDiagnosticsTestUtil {

    val defaultOptions = Analyzer.Options(
        includeStdLib = false,
    )

    fun compileExpectingDiagnostics(gapl: String, options: Analyzer.Options = defaultOptions): List<Diagnostic> {
        return try {
            val result = Analyzer.analyze(gapl, options)
            check(result.diagnostics.isNotEmpty()) { "expected diagnostics but got none" }
            result.diagnostics
        } catch (e: DiagnosticsException) {
            e.diagnostics
        }
    }
}
