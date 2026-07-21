package diagnostics

import com.uabutler.Compiler
import com.uabutler.diagnostics.Diagnostic
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.netlistir.transformer.Flattener
import kotlin.test.assertFailsWith

object DiagnosticsTestUtil {

    val defaultOptions = Compiler.Options(
        flattenMode = Flattener.Mode.NONE,
        literalSimplification = false,
        constantSimplification = false,
        includeStdLib = false,
        retime = null,
        retimingClockPeriod = null,
        retimingMinimizeRegisterCount = false,
        retimingMaintainTiming = false,
    )

    fun compileExpectingDiagnostics(gapl: String, options: Compiler.Options = defaultOptions): List<Diagnostic> {
        val exception = assertFailsWith<DiagnosticsException> { Compiler.compile(gapl, options) }
        return exception.diagnostics
    }
}
