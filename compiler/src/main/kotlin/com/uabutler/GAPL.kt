package com.uabutler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.uabutler.diagnostics.DiagnosticFormatter
import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.netlistir.transformer.Flattener.Mode
import com.uabutler.netlistir.transformer.RetimingSolverId
import com.uabutler.util.Logger
import com.uabutler.util.PropagationDelay
import com.uabutler.util.YamlDelayModel
import java.io.File
import kotlin.system.exitProcess

fun compile(inputFiles: List<File>, outputFile: File, options: Compiler.Options) {
    val gapl = inputFiles.joinToString("\n") { it.readText() }

    val verilog = try {
        Compiler.compile(gapl, options)
    } catch (e: DiagnosticsException) {
        e.diagnostics.forEach { println(DiagnosticFormatter.format(it, gapl)) }
        exitProcess(1)
    } catch (e: InvalidCompilerOptionsException) {
        println("Error: ${e.message}")
        exitProcess(1)
    } catch (e: Throwable) {
        // Anything reaching here escaped the diagnostics pipeline entirely - a bug in the
        // compiler (including unimplemented features, which throw NotImplementedError - a
        // kotlin.Error, not an Exception), not a mistake in the student's source. The stack
        // trace goes to the log (always visible - ERROR is the highest level) rather than
        // the primary message.
        println("Internal compiler error: this is a bug in the compiler, not your code. Please contact a TA.")
        Logger.error { e.stackTraceToString() }
        exitProcess(1)
    }

    outputFile.writeText(verilog)
}

fun createDelayModelFromFile(yaml: File): PropagationDelay {
    return YamlDelayModel(yaml)
}

object BuildInfo {
    val version: String = BuildInfo::class.java.`package`.implementationVersion ?: "dev"
}

/** Sentinel [Gapl.retimingClockPeriod] value meaning "not provided" / "min" - see [Compiler.Options.retimingClockPeriod]. */
private const val MIN_CLOCK_PERIOD = -1

class Gapl : CliktCommand(name = "gapl") {

    override fun help(context: Context) = "Compiles GAPL source files to Verilog."

    init {
        versionOption(BuildInfo.version)
    }

    private val inputFiles: List<File> by argument(
        name = "FILES",
        help = "GAPL source files to compile.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true).multiple(required = true)

    private val output: File by option(
        "-o", "--output",
        help = "Output Verilog file.",
    ).file(canBeDir = false).required()

    private val flatten: Mode? by option(
        "--flatten",
        help = "Inlining strategy. If omitted, derived from --retiming-solver when retiming is " +
            "requested (monolithic solvers require all, hierarchical solvers require none); " +
            "defaults to all otherwise.",
    ).choice(Mode.entries.associate { it.name.lowercase() to it })

    private val literalSimplification: Boolean by option(
        "--literal-simplification",
        help = "Dedupe identical literal nodes to one canonical node per (size, value).",
    ).flag("--no-literal-simplification", default = true)

    private val stdLib: Boolean by option(
        "--std-lib",
        help = "Prepend and include the GAPL standard library.",
    ).flag("--no-std-lib", default = true)

    private val constantSimplification: Boolean by option(
        "--constant-simplification",
        hidden = true, // experimental, unconditionally crashes - see compiler/CLAUDE.md gotchas
    ).flag(default = false)

    private val retime: File? by option(
        "--retime",
        help = "YAML delay-model file. Providing this enables retiming.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val retimingClockPeriod: Int by option(
        "--retiming-clock-period",
        help = "Target clock period for retiming: \"min\" or a positive integer. Defaults to min.",
    ).convert { raw ->
        if (raw.equals("min", ignoreCase = true)) MIN_CLOCK_PERIOD
        else raw.toIntOrNull()?.takeIf { it > 0 } ?: fail("must be \"min\" or a positive integer")
    }.default(MIN_CLOCK_PERIOD)

    private val retimingSolver: RetimingSolverId by option(
        "--retiming-solver",
        help = "Solver used for the final retiming. fast/minimal-register/scc/dag are monolithic " +
            "(require --flatten all); hierarchical-minimal-register is hierarchical (requires " +
            "--flatten none or recursive). dag additionally requires the circuit to be fully " +
            "acyclic (no register-protected loops).",
    ).choice(RetimingSolverId.entries.associate { it.id to it }).default(RetimingSolverId.FAST)

    private val retimingMinClockPeriodSolver: RetimingSolverId? by option(
        "--retiming-min-clock-period-solver",
        help = "Solver used when searching for the minimum clock period (--retiming-clock-period " +
            "min). Must be the same kind (monolithic/hierarchical) as --retiming-solver. Defaults " +
            "to fast for monolithic solvers, hierarchical-minimal-register for hierarchical solvers.",
    ).choice(RetimingSolverId.entries.associate { it.id to it })

    private val retimingMaintainsTiming: Boolean by option(
        "--retiming-maintains-timing",
        help = "Should the retiming algorithm maintain the timing characteristics of the original circuit?",
    ).flag(default = false)

    private val logLevel: Logger.Level by option(
        "--log-level",
        help = "Logging verbosity.",
    ).choice(Logger.Level.entries.associate { it.name.lowercase() to it }).default(Logger.Level.ERROR)

    override fun run() {
        Logger.setLevel(logLevel)

        val options = Compiler.Options(
            flattenMode = flatten,
            literalSimplification = literalSimplification,
            constantSimplification = constantSimplification,
            includeStdLib = stdLib,
            retime = retime?.let { createDelayModelFromFile(it) },
            retimingClockPeriod = retimingClockPeriod.takeIf { it != MIN_CLOCK_PERIOD },
            retimingSolverId = retimingSolver,
            retimingMinClockPeriodSolverId = retimingMinClockPeriodSolver,
            retimingMaintainTiming = retimingMaintainsTiming,
        )

        compile(inputFiles, output, options)
    }
}

fun main(args: Array<String>) = Gapl().main(args)
