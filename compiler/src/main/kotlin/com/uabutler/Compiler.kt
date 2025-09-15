package com.uabutler

import com.uabutler.netlistir.builder.ModuleBuilder
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.transformer.Flattener
import com.uabutler.netlistir.transformer.LiteralSimplifier
import com.uabutler.netlistir.transformer.PassThroughRemover
import com.uabutler.netlistir.transformer.Renamer
import com.uabutler.netlistir.transformer.Retimer
import com.uabutler.netlistir.transformer.retiming.delay.PropagationDelay
import com.uabutler.resolver.Resolver
import com.uabutler.util.standardLibary
import com.uabutler.verilogir.builder.VerilogBuilder

object Compiler {

    data class Options(
        val flatten: Boolean,
        val literalSimplification: Boolean,
        val includeStdLib: Boolean,
        val retime: PropagationDelay?,
    )

    fun runNetlistTransformers(inputNetlist: List<Module>, options: Options): List<Module> {
        val transformers = buildList {
            if (options.flatten) add(Flattener)

            if (options.literalSimplification) add(LiteralSimplifier)

            add(PassThroughRemover)

            if (options.retime != null) add(Retimer(options.retime))

            add(Renamer)
        }

        return transformers.fold(inputNetlist) { acc, transformer -> transformer.transform(acc) }
    }

    fun preprocessor(gapl: String, options: Options) = buildString {
        if (options.includeStdLib) appendLine(standardLibary)
        appendLine(gapl)
    }

    fun compile(gapl: String, options: Options): String {
        val initialNetlistModules = gapl
            .let { preprocessor(it, options) }
            .let { Parser.fromString(it).program() }
            .let { Resolver.cstToAst(it) }
            .let { ModuleBuilder(it).buildAllModules() }

        val transformedModules = runNetlistTransformers(initialNetlistModules, options)

        val verilogIrModules = transformedModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) }
        return verilogIrModules.joinToString("\n") { it.verilogSerialize() }
    }

}
