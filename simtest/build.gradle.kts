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
    var topModule: String? = null,
    var retimeDelayModel: String? = null,
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
            "topModule" -> testProperties.topModule = value.toString()
            "retime" -> if (value.toString().toBoolean()) testProperties.retimeDelayModel = testDirectory.listFiles()!!.first { it.isFile && it.name == "delay.yaml" }.absolutePath
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

        if (!properties.literalSimplication) { add("-ono-literal-simplication") }

        if (!properties.flatten) { add("-ono-flatten") }

        if (properties.retimeDelayModel != null) {
            add("-retime")
            add(properties.retimeDelayModel!!)
        }

        addAll(properties.additionalCompilerFlags)
        removeAll(properties.removeCompilerFlags)
    }
}

fun createVerilatorExeName(testDir: File) = "test_${testDir.name}"

fun createVerilatorSimCommand(testDir: File, verilogFiles: List<File>, cppFiles: List<File>, properties: TestProperties): List<String> {
    val objDir = layout.buildDirectory.dir("tests/${testDir.name}/cpp").get().asFile

    val gaplFiles = testDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
    val topFromSingleGapl = if (gaplFiles.size == 1) gaplFiles.first().nameWithoutExtension else null
    val top = properties.topModule ?: topFromSingleGapl

    if (top.isNullOrBlank()) {
        throw Exception("⚠️  Could not determine top module for '${testDir.name}'. Provide tests/${testDir.name}/top.txt or use a single .gapl file.")
    }

    val exeName = createVerilatorExeName(testDir)

    return buildList {
        addAll(listOf("verilator", "-Wall", "-Wno-UNUSEDSIGNAL", "-Wno-DECLFILENAME", "-cc"))
        addAll(verilogFiles.map { it.absolutePath })
        addAll(listOf("--top-module", top, "-Mdir", objDir.absolutePath))
        add("--exe")
        addAll(cppFiles.map { it.absolutePath })
        addAll(listOf("--build", "-o", exeName))

        addAll(properties.additionalVerilatorFlags)
        removeAll(properties.removeVerilatorFlags)
    }
}

val testsRoot = file("tests")

/**
 * Generates Verilog for each test directory.
 * For each subdirectory under simtest/tests containing *.gapl files, compiles them into build/tests/<name>/verilog.
 */
tasks.register("generateVerilog") {
    group = "simtest"
    description = "Compile GAPL test sources to Verilog for each test directory"
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

        testsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { testDir ->
            val gaplFiles = testDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
            if (gaplFiles.isEmpty()) {
                println("Skipping '${testDir.name}': no .gapl files found.")
                return@forEach
            }

            val outDir = layout.buildDirectory.dir("tests/${testDir.name}/verilog").get().asFile
            outDir.mkdirs()

            gaplFiles.forEach { gapl ->
                val outV = File(outDir, "${gapl.nameWithoutExtension}.v")
                println("Compiling ${testDir.name}/${gapl.name} -> ${outV.relativeTo(project.projectDir)}")

                val err = ByteArrayOutputStream()
                val result = exec {
                    isIgnoreExitValue = true
                    commandLine(createGaplCompileCommand(gapl, outV, loadTestProperties(testDir)))
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

        testsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { testDir ->
            val objDir = layout.buildDirectory.dir("tests/${testDir.name}/cpp").get().asFile
            val cppFiles = testDir.listFiles { f -> f.isFile && f.extension.lowercase() == "cpp" }?.toList().orEmpty()

            // Determine top module (same logic as earlier)
            if (cppFiles.isEmpty()) {
                println("Skipping '${testDir.name}': no C++ wrapper (*.cpp) found.")
                return@forEach
            }

            // Locate generated Verilog
            val verilogDir = layout.buildDirectory.dir("tests/${testDir.name}/verilog").get().asFile
            val verilogFiles = verilogDir.listFiles { f -> f.isFile && f.extension == "v" }?.toList().orEmpty()
            if (verilogFiles.isEmpty()) {
                println("Skipping '${testDir.name}': no generated Verilog found. Run :simtest:generateVerilog first.")
                hasFailure = true
                return@forEach
            }

            // Use Verilator to build the executable directly, leveraging the generated model and wrapper(s)
            val buildErr = ByteArrayOutputStream()
            val buildRes = exec {
                isIgnoreExitValue = true
                commandLine(createVerilatorSimCommand(testDir, verilogFiles, cppFiles, loadTestProperties(testDir)))
                errorOutput = buildErr
                standardOutput = buildErr
            }
            if (buildRes.exitValue != 0) {
                hasFailure = true
                println("❌ Building test executable failed for '${testDir.name}'")
                val msg = buildErr.toString().trim()
                if (msg.isNotEmpty()) {
                    println("---- Verilator Build Output ----")
                    println(msg)
                    println("-------------------------------")
                }
                return@forEach
            }

            val exe = File(objDir, createVerilatorExeName(testDir))
            if (!exe.exists()) {
                hasFailure = true
                println("❌ Expected test executable not found in ${objDir.absolutePath}")
                return@forEach
            }

            // Run the test executable
            val runErr = ByteArrayOutputStream()
            val runRes = exec {
                isIgnoreExitValue = true
                commandLine(exe.absolutePath)
                errorOutput = runErr
                standardOutput = runErr
            }
            if (runRes.exitValue != 0) {
                hasFailure = true
                println("❌ Test '${testDir.name}' failed (exit code ${runRes.exitValue})")
                val msg = runErr.toString().trim()
                if (msg.isNotEmpty()) {
                    println("---- Test Output ----")
                    println(msg)
                    println("---------------------")
                }
            } else {
                println("✅ Test '${testDir.name}' passed")
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
