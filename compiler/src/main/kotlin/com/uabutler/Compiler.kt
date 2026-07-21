package com.uabutler

import com.uabutler.diagnostics.Diagnostic
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
import com.uabutler.resolver.Resolver
import com.uabutler.util.Logger
import com.uabutler.util.standardLibary
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

    fun preprocessor(gapl: String, options: Options) = buildString {
        if (options.includeStdLib) appendLine(standardLibary)
        appendLine(gapl)
    }

    // The number of lines occupied by the prepended standard library in the preprocessed source,
    // used to translate diagnostic spans back to the user's original source before reporting them.
    fun stdLibLineOffset(options: Options) = if (options.includeStdLib) standardLibary.count { it == '\n' } + 1 else 0

    // Diagnostics are collected against the preprocessed (stdlib-prepended) source; shift them back
    // to the user's own source before they're ever reported, flagging the rare case where an error
    // genuinely falls inside the prepended stdlib text as a likely compiler bug instead of showing
    // a nonsensical negative line number.
    private fun forUserSource(diagnostics: List<Diagnostic>, options: Options): List<Diagnostic> {
        val offset = stdLibLineOffset(options)

        return diagnostics.map { diagnostic ->
            if (diagnostic.span.startLine - offset <= 0) {
                diagnostic.copy(note = "this location is inside the prepended standard library, not your source - if you did not modify the standard library, this is likely a compiler bug; please contact a TA")
            } else {
                diagnostic.shiftedLines(-offset)
            }
        }
    }

    fun compile(gapl: String, options: Options): String {
        val preprocessed = Logger.run("Preprocessing", Logger.Level.INFO) { preprocessor(gapl, options) }

        val program = try {
            Logger.run("Parser", Logger.Level.INFO) { Parser.fromString(preprocessed).program() }
        } catch (e: DiagnosticsException) {
            throw DiagnosticsException(forUserSource(e.diagnostics, options))
        }

        val analysis = Logger.run("Resolver", Logger.Level.INFO) { Resolver.analyze(program) }

        if (analysis.diagnostics.isNotEmpty()) {
            throw DiagnosticsException(forUserSource(analysis.diagnostics, options))
        }

        val netlistResult = Logger.run("Netlist Builder", Logger.Level.INFO) { ModuleBuilder(analysis.ast).buildAllModules() }

        if (netlistResult.diagnostics.isNotEmpty()) {
            throw DiagnosticsException(forUserSource(netlistResult.diagnostics, options))
        }

        val initialNetlistModules = netlistResult.modules

        val transformedModules = Logger.run("Transformers", Logger.Level.INFO) { runNetlistTransformers(initialNetlistModules, options) }

        val verilogIrModules = Logger.run("Verilog IR Builder", Logger.Level.INFO) { transformedModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) } }
        return Logger.run("Verilog Serializer", Logger.Level.INFO) { verilogIrModules.joinToString("\n") { it.verilogSerialize() } }
    }

}
