package com.uabutler

import com.uabutler.util.Logger
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
        constantSimplification = !parsedArgs.containsKey("-ono-constant-simplification"),
        includeStdLib = !parsedArgs.containsKey("-no-std-lib"),
        retime = parsedArgs["-retime"]?.let { createDelayModelFromFile(File(it.first())) },
        retimingClockPeriod = parsedArgs["-retiming-clock-period"]?.first()?.lowercase()?.let { if (it == "min") null else it.toIntOrNull() },
        retimingMinimizeRegisterCount = parsedArgs.containsKey("-retiming-minimize-register-count"),
        retimingMaintainTiming = parsedArgs.containsKey("-retiming-maintains-timing"),
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
    println("  Retiming Clock Period")
    println("    Usage:       [-retiming-clock-period min|CLOCK_PERIOD]")
    println("    Description: What clock period should the retiming algorithm target? Either \"min\" or an integer. Defaults to min.")
    println("  Retiming Minimize Register Count")
    println("    Usage:       [-retiming-minimize-register-count]")
    println("    Description: Produce a circuit with the smallest number of registers possible given the clock period constraint.")
    println("  Retiming Maintains Timing")
    println("    Usage:       [-retiming-maintains-timing]")
    println("    Description: Should the retiming algorithm maintain the timing characteristics of the original circuit?")
    println("  Standard Library")
    println("    Usage:       [-no-std-lib]")
    println("    Description: Defaults to true. Providing this option disables inclusion of the standard library, which is prepended.")
    println("  Logging Level")
    println("    Usage:       -log-level debug|info|warn|error")
    println("    Description: The logging level. Defaults to INFO.")
}

object BuildInfo {
    val version: String = BuildInfo::class.java.`package`.implementationVersion ?: "dev"
}

fun printVersion() {
    println("GAPL version ${BuildInfo.version}")
}

fun main(args : Array<String>) {
    val parsedArgs = parseArgs(args)

    if (parsedArgs.containsKey("-log-level")) {
        val level = parsedArgs["-log-level"]?.first()

        if (level == null) {
            println("Error: Logging level not specified")
            exitProcess(1)
        }

        when (level.uppercase()) {
            "TRACE" -> Logger.setLevel(Logger.Level.TRACE)
            "DEBUG" -> Logger.setLevel(Logger.Level.DEBUG)
            "INFO"  -> Logger.setLevel(Logger.Level.INFO)
            "WARN"  -> Logger.setLevel(Logger.Level.WARN)
            "ERROR" -> Logger.setLevel(Logger.Level.ERROR)
            else -> {
                println("Error: Invalid logging level: $level")
                exitProcess(1)
            }
        }
    } else {
        Logger.setLevel(Logger.Level.INFO)
    }

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
