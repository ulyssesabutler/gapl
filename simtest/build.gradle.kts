import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import kotlin.collections.first
import kotlin.collections.orEmpty
import kotlin.io.nameWithoutExtension

plugins {
    base
}

data class TestProperties(
    var flatten: Boolean = true,
    var literalSimplication: Boolean = true,
    var waveform: Boolean = false,
    var topModule: String? = null,
    var retimeDelayModel: String? = null,
    var retimingClockPeriod: String? = null,
    var retimingSolver: String? = null,
    var retimingMaintainTiming: Boolean = false,
    val additionalCompilerFlags: MutableList<String> = mutableListOf(),
    val removeCompilerFlags: MutableList<String> = mutableListOf(),
    val additionalVerilatorFlags: MutableList<String> = mutableListOf(),
    val removeVerilatorFlags: MutableList<String> = mutableListOf(),
)

fun loadTestProperties(testDirectory: File): TestProperties {
    val props = Properties()
    val propertiesFile = testDirectory.listFiles()!!.firstOrNull { it.isFile && it.name == "test.properties" }

    if (propertiesFile != null && propertiesFile.exists()) {
        propertiesFile.inputStream().use {
            props.load(it)
        }
    }

    val testProperties = TestProperties()

    props.forEach { (name, value) ->
        when (name.toString()) {
            "flatten" -> testProperties.flatten = value.toString().toBoolean()
            "literalSimplication" -> testProperties.literalSimplication = value.toString().toBoolean()
            "waveform" -> testProperties.waveform = value.toString().toBoolean()
            "topModule" -> testProperties.topModule = value.toString()
            "retime" -> if (value.toString().toBoolean()) testProperties.retimeDelayModel = testDirectory.listFiles()!!.first { it.isFile && it.name == "delay.yaml" }.absolutePath
            "retimingClockPeriod" -> testProperties.retimingClockPeriod = value.toString()
            "retimingSolver" -> testProperties.retimingSolver = value.toString()
            "retimingMaintainTiming" -> testProperties.retimingMaintainTiming = value.toString().toBoolean()
            "additionalCompilerFlags" -> testProperties.additionalCompilerFlags += value.toString().split(",")
            "additionalVerilatorFlags" -> testProperties.additionalVerilatorFlags += value.toString().split(",")
            "removeCompilerFlags" -> testProperties.removeCompilerFlags += value.toString().split(",")
            "removeVerilatorFlags" -> testProperties.removeVerilatorFlags += value.toString().split(",")
        }
    }

    return testProperties
}

fun createGaplCompileCommand(gaplFile: File, outputVerilogFile: File, properties: TestProperties): List<String> {
    val compiler = project(":compiler")
        .layout.buildDirectory.file("install/gapl/bin/gapl").get().asFile

    if (!compiler.exists()) {
        throw GradleException("GAPL compiler not found at $compiler")
    }

    return buildList {
        add(compiler.absolutePath)
        add("-i")
        add(gaplFile.absolutePath)
        add("-o")
        add(outputVerilogFile.absolutePath)

        if (!properties.literalSimplication) { add("-ono-literal-simplification") }

        if (!properties.flatten) {
            add("-flatten")
            add("none")
        }

        if (properties.retimeDelayModel != null) {
            add("-retime")
            add(properties.retimeDelayModel!!)
        }

        if (properties.retimingClockPeriod != null) {
            add("-retiming-clock-period")
            add(properties.retimingClockPeriod.toString())
        }

        if (properties.retimingSolver != null) {
            add("-retiming-solver")
            add(properties.retimingSolver!!)
        }

        if (properties.retimingMaintainTiming) { add("-retiming-maintains-timing") }

        add("-log-level")
        add("DEBUG")

        addAll(properties.additionalCompilerFlags)
        removeAll(properties.removeCompilerFlags)
    }
}

/**
 * A single test to run: the shared GAPL/C++ sources live in [programDir], while
 * [variationDir] (a subdirectory of it) holds just that variation's test.properties/delay.yaml.
 */
data class TestCase(val programDir: File, val variationDir: File) {
    val qualifiedName get() = "${programDir.name}/${variationDir.name}"
    val id get() = "${programDir.name}_${variationDir.name}"
}

/**
 * Every subdirectory of [testsRoot] is a program; every subdirectory of a program
 * is one of its variations (e.g. tests/md5/{unretimed,retimed}).
 */
fun discoverTestCases(testsRoot: File): List<TestCase> =
    testsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.flatMap { programDir ->
        programDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.map { variationDir ->
            TestCase(programDir, variationDir)
        }.orEmpty()
    }.orEmpty()

fun createVerilatorExeName(testCase: TestCase) = "test_${testCase.id}"

fun createVerilatorSimCommand(testCase: TestCase, verilogFiles: List<File>, cppFiles: List<File>, properties: TestProperties): List<String> {
    val objDir = layout.buildDirectory.dir("tests/${testCase.qualifiedName}/cpp").get().asFile

    val gaplFiles = testCase.programDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
    val topFromSingleGapl = if (gaplFiles.size == 1) gaplFiles.first().nameWithoutExtension else null
    val top = properties.topModule ?: topFromSingleGapl

    if (top.isNullOrBlank()) {
        throw Exception("⚠️  Could not determine top module for '${testCase.qualifiedName}'. Provide tests/${testCase.programDir.name}/top.txt or use a single .gapl file.")
    }

    val exeName = createVerilatorExeName(testCase)

    return buildList {
        addAll(listOf("verilator", "-Wall", "-Wno-UNUSEDSIGNAL", "-Wno-DECLFILENAME", "-cc"))
        addAll(verilogFiles.map { it.absolutePath })
        addAll(listOf("--top-module", top, "-Mdir", objDir.absolutePath))
        add("--exe")
        addAll(cppFiles.map { it.absolutePath })
        addAll(listOf("--build", "-o", exeName))
        if (properties.waveform) add("--trace")

        addAll(properties.additionalVerilatorFlags)
        removeAll(properties.removeVerilatorFlags)
    }
}

val testsRoot = file("tests")

/**
 * Generates Verilog for each test case.
 * For each tests/<program>/<variation> directory pair, compiles <program>'s *.gapl files
 * (using <variation>'s test.properties/delay.yaml) into build/tests/<program>/<variation>/verilog.
 */
tasks.register("generateVerilog") {
    group = "simtest"
    description = "Compile GAPL test sources to Verilog for each test case"
    dependsOn(":compiler:installDist")

    // Inputs/Outputs for gradle up-to-date checks
    inputs.files(fileTree(testsRoot) { include("**/*.gapl") })
    outputs.dir(layout.buildDirectory.dir("tests"))

    doLast {
        if (!testsRoot.exists()) {
            println("No tests directory found at: ${testsRoot.absolutePath}. Skipping.")
            return@doLast
        }

        var hasFailure = false

        discoverTestCases(testsRoot).forEach { testCase ->
            val gaplFiles = testCase.programDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
            if (gaplFiles.isEmpty()) {
                println("Skipping '${testCase.qualifiedName}': no .gapl files found.")
                return@forEach
            }

            val outDir = layout.buildDirectory.dir("tests/${testCase.qualifiedName}/verilog").get().asFile
            outDir.mkdirs()

            gaplFiles.forEach { gapl ->
                val outV = File(outDir, "${gapl.nameWithoutExtension}.v")
                println("Compiling ${testCase.qualifiedName}/${gapl.name} -> ${outV.relativeTo(project.projectDir)}")

                val command = createGaplCompileCommand(gapl, outV, loadTestProperties(testCase.variationDir))
                println("  Using ${command.drop(1).joinToString(" ")}")

                val err = ByteArrayOutputStream()
                val result = exec {
                    isIgnoreExitValue = true
                    commandLine(command)
                    errorOutput = err
                    standardOutput = err
                }
                if (result.exitValue != 0) {
                    hasFailure = true
                    println("❌ Failed to compile ${gapl.name}")
                    val msg = err.toString().trim()
                    if (msg.isNotEmpty()) {
                        println("---- Compiler Error Output ----")
                        println(msg)
                        println("-------------------------------")
                    }
                }
            }
        }

        if (hasFailure) {
            throw GradleException("One or more GAPL files failed to compile")
        }
    }
}

tasks.register("runSimulation") {
    group = "simtest"
    description = "Compile and run C++ test wrappers with Verilator-generated models"
    dependsOn("generateVerilog")

    doLast {
        var hasFailure = false

        discoverTestCases(testsRoot).forEach { testCase ->
            val testProperties = loadTestProperties(testCase.variationDir)

            val objDir = layout.buildDirectory.dir("tests/${testCase.qualifiedName}/cpp").get().asFile
            val cppFiles = testCase.programDir.listFiles { f -> f.isFile && f.extension.lowercase() == "cpp" }?.toList().orEmpty()

            // Determine top module (same logic as earlier)
            if (cppFiles.isEmpty()) {
                println("Skipping '${testCase.qualifiedName}': no C++ wrapper (*.cpp) found.")
                return@forEach
            }

            // Locate generated Verilog
            val verilogDir = layout.buildDirectory.dir("tests/${testCase.qualifiedName}/verilog").get().asFile
            val verilogFiles = verilogDir.listFiles { f -> f.isFile && f.extension == "v" }?.toList().orEmpty()
            if (verilogFiles.isEmpty()) {
                println("Skipping '${testCase.qualifiedName}': no generated Verilog found. Run :simtest:generateVerilog first.")
                hasFailure = true
                return@forEach
            }

            // The output waveform
            val waveformDir = layout.buildDirectory.dir("tests/${testCase.qualifiedName}/waveform").get().asFile
            val waveformFile = if (testProperties.waveform) {
                waveformDir.mkdirs()
                File(waveformDir, "${testCase.id}.vcd")
            } else {
                null
            }

            // Use Verilator to build the executable directly, leveraging the generated model and wrapper(s)
            val buildErr = ByteArrayOutputStream()
            val buildRes = exec {
                isIgnoreExitValue = true
                commandLine(createVerilatorSimCommand(testCase, verilogFiles, cppFiles, testProperties))
                errorOutput = buildErr
                standardOutput = buildErr
            }
            if (buildRes.exitValue != 0) {
                hasFailure = true
                println("❌ Building test executable failed for '${testCase.qualifiedName}'")
                val msg = buildErr.toString().trim()
                if (msg.isNotEmpty()) {
                    println("---- Verilator Build Output ----")
                    println(msg)
                    println("-------------------------------")
                }
                return@forEach
            }

            val exe = File(objDir, createVerilatorExeName(testCase))
            if (!exe.exists()) {
                hasFailure = true
                println("❌ Expected test executable not found in ${objDir.absolutePath}")
                return@forEach
            }

            // Run the test executable
            val runErr = ByteArrayOutputStream()
            val runRes = exec {
                isIgnoreExitValue = true
                commandLine(listOfNotNull(exe.absolutePath, waveformFile?.absolutePath))
                errorOutput = runErr
                standardOutput = runErr
            }
            if (runRes.exitValue != 0) {
                hasFailure = true
                println("❌ Test '${testCase.qualifiedName}' failed (exit code ${runRes.exitValue})")
                val msg = runErr.toString().trim()
                if (msg.isNotEmpty()) {
                    println("---- Test Output ----")
                    println(msg)
                    println("---------------------")
                }
            } else {
                println("✅ Test '${testCase.qualifiedName}' passed")
            }
        }

        if (hasFailure) {
            throw GradleException("One or more simulation tests failed")
        }
    }
}

// Make this module's build depend on both phases.
tasks.named("build") {
    dependsOn("compileVerilog", "runSimulation")
}
