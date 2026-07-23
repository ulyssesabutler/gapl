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

object Compiler {

    data class Options(
        val flattenMode: Flattener.Mode?,
        val literalSimplification: Boolean,
        val constantSimplification: Boolean,
        val includeStdLib: Boolean,
        val retime: PropagationDelay?,
        val retimingClockPeriod: Int?,
        val retimingSolverId: RetimingSolverId,
        val retimingMinClockPeriodSolverId: RetimingSolverId?,
        val retimingMaintainTiming: Boolean,
    ) {
        val analyzerOptions get() = Analyzer.Options(includeStdLib)
    }

    // -flatten is optional: when the user doesn't pass it, the required mode is derived from the
    // chosen retiming solver's kind (monolithic solvers need everything flattened; hierarchical
    // solvers need the native module hierarchy intact). When retiming isn't requested at all,
    // there's no solver kind to derive from, so this just preserves the old always-"all" default.
    // An explicit -flatten value that conflicts with the solver's requirement is a hard error,
    // never silently overridden.
    private fun resolveFlattenMode(options: Options): Flattener.Mode {
        if (options.retime == null) return options.flattenMode ?: Flattener.Mode.ALL

        val requiredKind = options.retimingSolverId.kind
        val requiredFlattenMode = when (requiredKind) {
            RetimingSolverKind.MONOLITHIC -> Flattener.Mode.ALL
            RetimingSolverKind.HIERARCHICAL -> Flattener.Mode.NONE
        }

        val explicit = options.flattenMode ?: return requiredFlattenMode
        val explicitKind = if (explicit == Flattener.Mode.ALL) RetimingSolverKind.MONOLITHIC else RetimingSolverKind.HIERARCHICAL
        if (explicitKind != requiredKind) {
            throw Exception(
                "-flatten $explicit is incompatible with -retiming-solver ${options.retimingSolverId.id} (requires a $requiredKind flatten mode)"
            )
        }
        return explicit
    }

    private fun resolveMinClockPeriodSolverId(options: Options): RetimingSolverId {
        val solverKind = options.retimingSolverId.kind
        val default = when (solverKind) {
            RetimingSolverKind.MONOLITHIC -> RetimingSolverId.FAST
            RetimingSolverKind.HIERARCHICAL -> RetimingSolverId.HIERARCHICAL_MINIMAL_REGISTER
        }

        val explicit = options.retimingMinClockPeriodSolverId ?: return default
        if (explicit.kind != solverKind) {
            throw Exception(
                "-retiming-min-clock-period-solver ${explicit.id} is a ${explicit.kind} solver, but -retiming-solver ${options.retimingSolverId.id} is $solverKind"
            )
        }
        return explicit
    }

    fun runNetlistTransformers(inputNetlist: List<Module>, options: Options): List<Module> {
        val effectiveFlattenMode = resolveFlattenMode(options)

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
                    val retimeMode = when (options.retimingSolverId.kind) {
                        RetimingSolverKind.MONOLITHIC -> Retimer.Mode.MONOLITH
                        RetimingSolverKind.HIERARCHICAL -> Retimer.Mode.HIERARCHICAL
                    }

                    add(Retimer(
                        mode = retimeMode,
                        delay = options.retime,
                        targetClockPeriod = options.retimingClockPeriod,
                        retimingSolverId = options.retimingSolverId,
                        minClockPeriodSolverId = resolveMinClockPeriodSolverId(options),
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
