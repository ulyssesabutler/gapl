package com.uabutler

import com.uabutler.diagnostics.DiagnosticsException
import com.uabutler.netlistir.builder.ModuleBuilder
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.transformer.ConstantSimplifier
import com.uabutler.netlistir.transformer.Flattener
import com.uabutler.netlistir.transformer.LiteralSimplifier
import com.uabutler.netlistir.transformer.PassThroughRemover
import com.uabutler.netlistir.transformer.Renamer
import com.uabutler.netlistir.transformer.Retimer
import com.uabutler.netlistir.transformer.StandardLibraryFilter
import com.uabutler.util.PropagationDelay
import com.uabutler.util.Logger
import com.uabutler.verilogir.builder.VerilogBuilder
import com.uabutler.verilogir.builder.creator.util.Identifier

object Compiler {

    data class Options(
        val flattenMode: Flattener.Mode,
        val literalSimplification: Boolean,
        val constantSimplification: Boolean,
        val includeStdLib: Boolean,
        val retime: PropagationDelay?,
        val retimingClockPeriod: Int?,
        val retimingMinimizeRegisterCount: Boolean,
        val retimingMaintainTiming: Boolean,
    ) {
        val analyzerOptions get() = Analyzer.Options(includeStdLib)
    }

    fun runNetlistTransformers(inputNetlist: List<Module>, options: Options): List<Module> {
        val transformers = Logger.run("Building Transformer List") {
            buildList {
                if (options.flattenMode != Flattener.Mode.NONE) {
                    Logger.debug { "Flattener" }
                    add(Flattener(options.flattenMode))
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
                    val retimeMode = when (options.flattenMode) {
                        Flattener.Mode.ALL -> Retimer.Mode.MONOLITH
                        Flattener.Mode.RECURSIVE,
                        Flattener.Mode.NONE -> Retimer.Mode.HIERARCHICAL
                    }

                    add(Retimer(
                        mode = retimeMode,
                        delay = options.retime,
                        targetClockPeriod = options.retimingClockPeriod,
                        minimizeRegisterCount = options.retimingMinimizeRegisterCount,
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
        val analysis = Logger.run("Analyzer", Logger.Level.INFO) { Analyzer.analyze(gapl, options.analyzerOptions) }

        if (analysis.diagnostics.isNotEmpty()) {
            throw DiagnosticsException(analysis.diagnostics)
        }

        val netlistResult = Logger.run("Netlist Builder", Logger.Level.INFO) { ModuleBuilder(analysis.ast).buildAllModules() }

        if (netlistResult.diagnostics.isNotEmpty()) {
            throw DiagnosticsException(Analyzer.shiftToUserSource(netlistResult.diagnostics, options.analyzerOptions))
        }

        val initialNetlistModules = netlistResult.modules

        val transformedModules = Logger.run("Transformers", Logger.Level.INFO) { runNetlistTransformers(initialNetlistModules, options) }

        val verilogIrModules = Logger.run("Verilog IR Builder", Logger.Level.INFO) { transformedModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) } }
        return Logger.run("Verilog Serializer", Logger.Level.INFO) { verilogIrModules.joinToString("\n") { it.verilogSerialize() } }
    }

}
