package com.uabutler

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

fun compilerOptions(parsedArgs: Map<String, List<String>>): Compiler.Options {
    return Compiler.Options(
        flatten = !parsedArgs.containsKey("-ono-flatten"),
        literalSimplification = !parsedArgs.containsKey("-ono-literal-simplification"),
        retime = null, // TODO
    )
}

fun main(args : Array<String>) {
    val parsedArgs = parseArgs(args)

    if (parsedArgs.containsKey("-i") && parsedArgs.containsKey("-o")) {
        val inputFiles = parsedArgs["-i"]!!
        val outputFile = parsedArgs["-o"]!!.first()

        compile(inputFiles, outputFile, compilerOptions(parsedArgs))
    } else {
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
        exitProcess(1)
    }
}
