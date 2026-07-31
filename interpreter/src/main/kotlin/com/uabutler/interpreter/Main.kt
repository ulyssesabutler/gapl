package com.uabutler.interpreter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.simengine.Engine
import com.uabutler.simgen.PortInspector
import com.uabutler.simgen.PortShape
import com.uabutler.simgen.RootModuleResolver
import com.uabutler.util.Logger
import com.uabutler.util.StandardLibraryFunctions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.io.File
import kotlin.system.exitProcess

private class EngineHandle(val engine: Engine, val inputShapes: Map<String, PortShape>, val outputShapes: Map<String, PortShape>)

private fun buildEngine(gaplSource: String, targetModuleName: String?): EngineHandle {
    val analysis = Analyzer.analyzeFull(gaplSource)
    if (analysis.modules == null) {
        error("Failed to compile GAPL source:\n" + analysis.diagnostics.joinToString("\n"))
    }
    val modules = analysis.modules!!
    // Same stdlib-root-filtering WrapperGenerator.generate does: unused stdlib helper functions are
    // themselves root modules by InvocationGraph's definition (no incoming invocation edges), so
    // they'd otherwise falsely trigger the "multiple root modules" error for a single-function design.
    val stdlibNames = StandardLibraryFunctions.entries.map { it.identifier }.toSet()
    val candidateRoots = InvocationGraph(modules).rootModules()
        .filterNot { it.invocation.gaplFunctionName in stdlibNames }
    val target: Module = RootModuleResolver.resolve(candidateRoots, targetModuleName)

    val engine = Engine.build(modules, target.invocation)
    val inputShapes = PortInspector.inputPorts(target).associate { it.name to it.shape }
    val outputShapes = PortInspector.outputPorts(target).associate { it.name to it.shape }
    return EngineHandle(engine, inputShapes, outputShapes)
}

fun runInterpreter(inputFiles: List<File>, cyclesFile: File, targetModuleName: String?) {
    val gapl = inputFiles.joinToString("\n") { it.readText() }

    try {
        val handle = buildEngine(gapl, targetModuleName)

        val cyclesJson = Json.parseToJsonElement(cyclesFile.readText()) as? JsonArray
            ?: error("Cycles file must contain a top-level JSON array, one element per clock cycle.")

        val results = CycleRunner.run(handle.engine, handle.inputShapes, handle.outputShapes, cyclesJson)

        var allPassed = true
        results.forEach { result ->
            if (result.passed) {
                println("cycle ${result.index}: ✅ passed")
            } else {
                allPassed = false
                println("cycle ${result.index}: ❌ failed")
                result.mismatches.forEach { mismatch ->
                    println(
                        "    ${mismatch.portName}: expected ${PortValueJson.toDisplayString(mismatch.expected)}, " +
                            "got ${PortValueJson.toDisplayString(mismatch.actual)}"
                    )
                }
            }
        }

        exitProcess(if (allPassed) 0 else 1)
    } catch (e: IllegalStateException) {
        // buildEngine/CycleRunner raise plain error()/IllegalStateException for every "expected"
        // failure (compile failure, ambiguous/missing root module, malformed cycles JSON) - mirrors
        // simgen's GenerateWrapper.kt.
        println("Error: ${e.message}")
        exitProcess(1)
    } catch (e: Throwable) {
        println("Internal interpreter error: this is a bug in interpreter, not your code. Please contact a TA.")
        Logger.error { e.stackTraceToString() }
        exitProcess(1)
    }
}

class Interpreter : CliktCommand(name = "interpreter") {

    override fun help(context: Context) =
        "Runs a GAPL design against a JSON array of cycle-by-cycle inputs and expected outputs."

    private val inputFiles: List<File> by argument(
        name = "FILES",
        help = "GAPL source files to compile.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true).multiple(required = true)

    private val cyclesFile: File by option(
        "-c", "--cycles",
        help = "JSON file: a top-level array, one object per clock cycle, keyed by port name. " +
            "An input port absent from a cycle holds its previous value; an output port absent from " +
            "a cycle isn't checked (\"don't care\").",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true).required()

    private val targetModule: String? by option(
        "--module",
        help = "Root module to run. Required if the source has more than one root module.",
    )

    override fun run() {
        runInterpreter(inputFiles, cyclesFile, targetModule)
    }
}

fun main(args: Array<String>) = Interpreter().main(args)
