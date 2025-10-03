package com.uabutler

import com.uabutler.util.PropagationDelay
import com.uabutler.util.YamlDelayModel
import java.io.File
import kotlin.system.exitProcess

fun parseArgs(args: Array<String>): Map<String, List<String>> {
    // https://stackoverflow.com/questions/53946908/functional-style-main-function-argument-parsing-for-kotlin
    return args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
        if (elem.startsWith("-"))  Pair(map + (elem to emptyList()), elem)
        else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first
}

fun compile(inputFiles: List<String>, outputFile: String, options: Compiler.Options) {
    val gapl = inputFiles.joinToString("\n") { File(it).readText() }
    val verilog = Compiler.compile(gapl, options)

    File(outputFile).writeText(verilog)
}

fun createDelayModelFromFile(yaml: File): PropagationDelay {
    return YamlDelayModel(yaml)
}

fun compilerOptions(parsedArgs: Map<String, List<String>>): Compiler.Options {
    return Compiler.Options(
        flatten = !parsedArgs.containsKey("-ono-flatten"),
        literalSimplification = !parsedArgs.containsKey("-ono-literal-simplification"),
        includeStdLib = !parsedArgs.containsKey("-no-std-lib"),
        retime = parsedArgs["-retime"]?.let { createDelayModelFromFile(File(it.first())) },
    )
}

fun printHelp() {
    println("Options:")
    println("  Input Files (REQUIRED)")
    println("    Usage:       -i INPUT_FILENAME[...]")
    println("    Description: A list of the input gapl files")
    println("  Output File (REQUIRED)")
    println("    Usage:       -o OUTPUT_FILENAME")
    println("    Description: The output verilog file")
    println("  Flatten")
    println("    Usage:       [-ono-flatten]")
    println("    Description: Defaults to true. Providing this option disables function inlining.")
    println("  Literal Simplification")
    println("    Usage:       [-ono-literal-simplification]")
    println("    Description: Defaults to true. Providing this option disables function inlining.")
    println("  Retime")
    println("    Usage:       -retime DELAY_MODEL_FILENAME")
    println("    Description: Provide a YAML file that specifies the delay model to be used.")
    println("  Standard Library")
    println("    Usage:       [-no-std-lib]")
    println("    Description: Defaults to true. Providing this option disables inclusion of the standard library, which is prepended.")
}

object BuildInfo {
    val version: String = BuildInfo::class.java.`package`.implementationVersion ?: "dev"
}

fun printVersion() {
    println("GAPL version ${BuildInfo.version}")
}

fun main(args : Array<String>) {
    val parsedArgs = parseArgs(args)

    if (parsedArgs.containsKey("-h")) {
        printHelp()
        exitProcess(0)
    } else if (parsedArgs.containsKey("-v")) {
        printVersion()
    } else if (parsedArgs.containsKey("-i") && parsedArgs.containsKey("-o")) {
        val inputFiles = parsedArgs["-i"]!!
        val outputFile = parsedArgs["-o"]!!.first()

        compile(inputFiles, outputFile, compilerOptions(parsedArgs))
    } else {
        println("Error: Invalid arguments. Use -h for help")
        exitProcess(1)
    }
}
