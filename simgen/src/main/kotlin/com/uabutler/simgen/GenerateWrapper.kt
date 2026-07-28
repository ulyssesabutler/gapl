package com.uabutler.simgen

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.uabutler.util.Logger
import java.io.File
import kotlin.system.exitProcess

fun generateWrapper(
    inputFiles: List<File>,
    outputDir: File,
    targetModuleName: String?,
    packageName: String,
    className: String?,
) {
    val gapl = inputFiles.joinToString("\n") { it.readText() }

    val fileSpec = try {
        WrapperGenerator.generate(
            gaplSource = gapl,
            targetModuleName = targetModuleName,
            packageName = packageName,
            className = className,
        )
    } catch (e: IllegalStateException) {
        // WrapperGenerator.generate raises plain error()/IllegalStateException for every
        // "expected" failure (compile failure, ambiguous/missing root module, interface
        // mismatch) - there's no structured DiagnosticsException to preserve here, unlike
        // Compiler.compile's three-way split.
        println("Error: ${e.message}")
        exitProcess(1)
    } catch (e: Throwable) {
        println("Internal simgen error: this is a bug in simgen, not your code. Please contact a TA.")
        Logger.error { e.stackTraceToString() }
        exitProcess(1)
    }

    outputDir.mkdirs()
    fileSpec.writeTo(outputDir.toPath())
}

class GenerateWrapper : CliktCommand(name = "simgen") {

    override fun help(context: Context) = "Generates a named-port Kotlin simulation wrapper class for a compiled GAPL design."

    private val inputFiles: List<File> by argument(
        name = "FILES",
        help = "GAPL source files to compile.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true).multiple(required = true)

    private val outputDir: File by option(
        "-o", "--output-dir",
        help = "Directory to write the generated Kotlin source into (package subdirectories are created automatically).",
    ).file(mustExist = false, canBeFile = false, canBeDir = true).required()

    private val targetModule: String? by option(
        "--module",
        help = "Root module to generate a wrapper for. Required if the source has more than one root module.",
    )

    private val packageName: String by option(
        "--package",
        help = "Package for the generated class.",
    ).default(WrapperGenerator.DEFAULT_PACKAGE_NAME)

    private val className: String? by option(
        "--class",
        help = "Name of the generated class. Defaults to the resolved module name + \"Simulator\".",
    )

    override fun run() {
        generateWrapper(inputFiles, outputDir, targetModule, packageName, className)
    }
}

fun main(args: Array<String>) = GenerateWrapper().main(args)
