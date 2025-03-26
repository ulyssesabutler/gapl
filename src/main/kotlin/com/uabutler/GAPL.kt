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

fun compile(inputFiles: List<String>, outputFile: String) {
    val gapl = inputFiles.joinToString("\n") { File(it).readText() }
    val verilog = Compiler.compile(gapl)

    File(outputFile).writeText(verilog)
}

fun test(inputDirectory: String) {
    File(inputDirectory).listFiles()?.forEach { file ->
        val name = file.name

        val gapl = file.readText()

        print("\u001b[31m")
        println("############################")
        print("#### TEST: ")
        print("\u001b[0m")

        print("\u001b[35m")
        println(name)
        print("\u001b[0m")

        print("\u001b[32m")
        println("## GAPL ##\n")
        print("\u001b[0m")

        print("\u001b[36m")
        println(gapl)
        print("\u001b[0m")

        val verilog = Compiler.compile(gapl)

        print("\u001b[32m")
        println("\n## VERILOG ##\n")
        print("\u001b[0m")

        print("\u001b[34m")
        println(verilog)
        print("\u001b[0m")
    }
}

fun cli(args: Array<String>) {
    val parsedArgs = parseArgs(args)

    if (parsedArgs.containsKey("-i") && parsedArgs.containsKey("-o")) {
        val inputFiles = parsedArgs["-i"]!!
        val outputFile = parsedArgs["-o"]!!.first()

        compile(inputFiles, outputFile)
    } else if (parsedArgs.containsKey("--test")) {
        val input = parsedArgs["--test"]!!.first()

        test(input)
    } else {
        println("Usage:")
        println("  gapl -i INPUT_FILENAME[...] -o OUTPUT_FILENAME")
        println("  gapl --test INPUT_DIRECTORY")
        exitProcess(1)
    }
}

fun main(args : Array<String>) = cli(args)
// fun main(args : Array<String>) = test()
