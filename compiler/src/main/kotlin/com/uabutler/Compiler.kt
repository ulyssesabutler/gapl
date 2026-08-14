package com.uabutler

import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.transformer.ConstantSimplifier
import com.uabutler.netlistir.transformer.Flattener
import com.uabutler.netlistir.transformer.LiteralSimplifier
import com.uabutler.netlistir.transformer.PassThroughRemover
import com.uabutler.netlistir.transformer.Renamer
import com.uabutler.netlistir.transformer.Retimer
import com.uabutler.netlistir.transformer.RetimingSolverId
import com.uabutler.netlistir.transformer.RetimingSolverKind
import com.uabutler.netlistir.transformer.StandardLibraryFilter
import com.uabutler.util.PropagationDelay
import com.uabutler.util.Logger
import com.uabutler.verilogir.builder.VerilogBuilder
import com.uabutler.verilogir.builder.creator.util.Identifier

/**
 * Thrown for a self-consistent but invalid combination of [Compiler.Options] (e.g. an explicit
 * --flatten value incompatible with the chosen --retiming-solver) — a mistake in how the compiler
 * was invoked, not a bug in the compiler itself or an error in the GAPL source. Callers should
 * report this the same way as a CLI usage error, not as an internal compiler error.
 */
class InvalidCompilerOptionsException(message: String) : Exception(message)

/**
 * Thrown when a solver could not find any retiming of the circuit that meets the requested clock
 * period — the target is genuinely unreachable given the circuit's own critical path/register
 * structure, not a bug in the compiler or a self-inconsistent set of options. Callers should
 * report this the same way as a CLI usage error, not as an internal compiler error.
 */
class RetimingInfeasibleException(message: String) : Exception(message)

object Compiler {

    data class Options(
        val flattenMode: Flattener.Mode?,
        val literalSimplification: Boolean,
        val constantSimplification: Boolean,
        val includeStdLib: Boolean,
        val retime: PropagationDelay?,
        val retimingClockPeriod: Int?,
        val retimingSolverId: RetimingSolverId?,
        val retimingMinClockPeriodSolverId: RetimingSolverId?,
        val retimingMaintainTiming: Boolean,
    ) {
        val analyzerOptions get() = Analyzer.Options(includeStdLib)
    }

    // --retiming-solver is optional. Given one, it decides everything, including which --flatten
    // mode is required. Without one, the choice follows whatever --flatten asks for: `all` is
    // monolithic territory, and anything else needs a hierarchical solver - where per-port is the
    // default, because it handles every case the whole-module solver does *plus* designs whose
    // caller holds a feedback loop through a submodule, which a whole-module boundary summary
    // cannot express at any clock period. `hierarchical-minimal-register` remains selectable
    // explicitly.
    private fun resolveRetimingSolverId(options: Options): RetimingSolverId {
        options.retimingSolverId?.let { return it }
        return when (options.flattenMode) {
            null, Flattener.Mode.ALL -> RetimingSolverId.FAST
            Flattener.Mode.NONE, Flattener.Mode.RECURSIVE -> RetimingSolverId.PER_PORT_HIERARCHICAL_MINIMAL_REGISTER
        }
    }

    // -flatten is optional: when the user doesn't pass it, the required mode is derived from the
    // chosen retiming solver's kind (monolithic solvers need everything flattened; hierarchical
    // solvers need the native module hierarchy intact). When retiming isn't requested at all,
    // there's no solver kind to derive from, so this just preserves the old always-"all" default.
    // An explicit -flatten value that conflicts with the solver's requirement is a hard error,
    // never silently overridden.
    private fun resolveFlattenMode(options: Options, solverId: RetimingSolverId): Flattener.Mode {
        if (options.retime == null) return options.flattenMode ?: Flattener.Mode.ALL

        val requiredKind = solverId.kind
        val requiredFlattenMode = when (requiredKind) {
            RetimingSolverKind.MONOLITHIC -> Flattener.Mode.ALL
            RetimingSolverKind.HIERARCHICAL -> Flattener.Mode.NONE
        }

        val explicit = options.flattenMode ?: return requiredFlattenMode
        val explicitKind = if (explicit == Flattener.Mode.ALL) RetimingSolverKind.MONOLITHIC else RetimingSolverKind.HIERARCHICAL
        if (explicitKind != requiredKind) {
            throw InvalidCompilerOptionsException(
                "--flatten $explicit is incompatible with --retiming-solver ${solverId.id} (requires a $requiredKind flatten mode)"
            )
        }
        return explicit
    }

    private fun resolveMinClockPeriodSolverId(options: Options, solverId: RetimingSolverId): RetimingSolverId {
        val solverKind = solverId.kind
        val default = when (solverKind) {
            RetimingSolverKind.MONOLITHIC -> RetimingSolverId.FAST
            // Hierarchical retimers search with their own solver rather than a cheaper oracle, so
            // the only coherent default is the solver actually selected - defaulting to a *different*
            // hierarchical solver would search against a different feasibility notion than the one
            // used for the final retiming.
            RetimingSolverKind.HIERARCHICAL -> solverId
        }

        val explicit = options.retimingMinClockPeriodSolverId ?: return default
        if (explicit.kind != solverKind) {
            throw InvalidCompilerOptionsException(
                "--retiming-min-clock-period-solver ${explicit.id} is a ${explicit.kind} solver, but --retiming-solver ${solverId.id} is $solverKind"
            )
        }
        return explicit
    }

    fun runNetlistTransformers(inputNetlist: List<Module>, options: Options): List<Module> {
        val effectiveRetimingSolverId = resolveRetimingSolverId(options)
        val effectiveFlattenMode = resolveFlattenMode(options, effectiveRetimingSolverId)

        val transformers = Logger.run("Building Transformer List") {
            buildList {
                if (effectiveFlattenMode != Flattener.Mode.NONE) {
                    Logger.debug { "Flattener" }
                    add(Flattener(effectiveFlattenMode))
                }

                if (options.includeStdLib) {
                    Logger.debug { "Standard Library Filter" }
                    add(StandardLibraryFilter)
                }

                if (options.constantSimplification) {
                    Logger.debug { "Constant Simplifier" }
                    add(ConstantSimplifier)
                    TODO()
                }

                if (options.literalSimplification) {
                    Logger.debug { "Literal Simplifier" }
                    add(LiteralSimplifier)
                }

                Logger.debug { "PassThrough Remover" }
                add(PassThroughRemover)

                if (options.retime != null) {
                    val retimeMode = when (effectiveRetimingSolverId.kind) {
                        RetimingSolverKind.MONOLITHIC -> Retimer.Mode.MONOLITH
                        RetimingSolverKind.HIERARCHICAL -> Retimer.Mode.HIERARCHICAL
                    }

                    add(Retimer(
                        mode = retimeMode,
                        delay = options.retime,
                        targetClockPeriod = options.retimingClockPeriod,
                        retimingSolverId = effectiveRetimingSolverId,
                        minClockPeriodSolverId = resolveMinClockPeriodSolverId(options, effectiveRetimingSolverId),
                        maintainTiming = options.retimingMaintainTiming,
                    ))
                }

                Logger.debug { "Renamer" }
                add(Renamer)
            }
        }

        return Logger.run("Running Transformers") {
            transformers.fold(inputNetlist) { intermediate, transformer ->
                Logger.run("Running ${transformer::class.simpleName}", Logger.Level.INFO) {
                    transformer.transform(intermediate).also {
                        Logger.run("Original Module List", Logger.Level.TRACE) {
                            Logger.trace { "${intermediate.size} modules" }
                            intermediate.forEach { Logger.trace { Identifier.module(it.invocation) } }
                        }
                        Logger.run("Transformed Module List", Logger.Level.TRACE) {
                            Logger.trace { "${it.size} modules" }
                            it.forEach { Logger.trace { Identifier.module(it.invocation) } }
                        }
                    }
                }
            }
        }
    }

    fun compile(gapl: String, options: Options): String {
        val analysis = Logger.run("Analyzer", Logger.Level.INFO) { Analyzer.analyzeFull(gapl, options.analyzerOptions) }

        if (analysis.diagnostics.isNotEmpty()) {
            throw DiagnosticsException(analysis.diagnostics)
        }

        val initialNetlistModules = analysis.modules!!

        val transformedModules = Logger.run("Transformers", Logger.Level.INFO) { runNetlistTransformers(initialNetlistModules, options) }

        val verilogIrModules = Logger.run("Verilog IR Builder", Logger.Level.INFO) { transformedModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) } }
        return Logger.run("Verilog Serializer", Logger.Level.INFO) { verilogIrModules.joinToString("\n") { it.verilogSerialize() } }
    }

}
