import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    base
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

        val compiler = project(":compiler")
            .layout.buildDirectory.file("install/gapl/bin/gapl").get().asFile

        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
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
                    commandLine(compiler, "-i", gapl.absolutePath, "-o", outV.absolutePath)
                    errorOutput = err
                    standardOutput = System.out
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

/**
 * Generate C++ model files from Verilog using Verilator.
 * This step generates the C++ files that wrap your Verilog module.
 */
tasks.register("compileVerilog") {
    group = "simtest"
    description = "Generate C++ model files from Verilog using Verilator"
    dependsOn("generateVerilog")

    doLast {
        var hasFailure = false

        val verilogRoot = layout.buildDirectory.dir("tests").get().asFile
        if (!verilogRoot.exists()) {
            println("No generated Verilog found. Skipping.")
            return@doLast
        }

        testsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { testDir ->
            val verilogDir = layout.buildDirectory.dir("tests/${testDir.name}/verilog").get().asFile
            val verilogFiles = verilogDir.listFiles { f -> f.isFile && f.extension == "v" }?.toList().orEmpty()

            if (verilogFiles.isEmpty()) {
                println("Skipping '${testDir.name}': no generated Verilog found.")
                return@forEach
            }

            // Determine top module (similar to your existing code)
            val topFromFile = File(testDir, "top.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
            val gaplFiles = testDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
            val topFromSingleGapl = if (gaplFiles.size == 1) gaplFiles.first().nameWithoutExtension else null

            val top = topFromFile ?: topFromSingleGapl
            if (top.isNullOrBlank()) {
                println("⚠️  Could not determine top module for '${testDir.name}'. Provide tests/${testDir.name}/top.txt, or use a single .gapl file.")
                return@forEach
            }

            val objDir = layout.buildDirectory.dir("tests/${testDir.name}/cpp").get().asFile
            objDir.mkdirs()

            println("Generating C++ model for '${testDir.name}' with top module '$top'")

            val err = ByteArrayOutputStream()
            val cmd = mutableListOf<String>().apply {
                addAll(listOf("verilator", "-Wall", "-Wno-UNUSEDSIGNAL", "-cc"))
                addAll(verilogFiles.map { it.absolutePath })
                addAll(listOf("--top-module", top, "-Mdir", objDir.absolutePath))
            }

            val res = exec {
                isIgnoreExitValue = true
                commandLine(cmd)
                errorOutput = err
                standardOutput = System.out
            }

            if (res.exitValue != 0) {
                hasFailure = true
                println("❌ Verilator C++ generation failed for '${testDir.name}'")
                val msg = err.toString().trim()
                if (msg.isNotEmpty()) {
                    println("---- Verilator Output ----")
                    println(msg)
                    println("--------------------------")
                }
            } else {
                println("✅ C++ model generated in ${objDir.relativeTo(project.projectDir)}")
            }

            if (hasFailure) {
                throw GradleException("One or more Verilog files failed to compile")
            }
        }
    }
}

tasks.register("runSimulation") {
    group = "simtest"
    description = "Compile and run C++ test wrappers with Verilator-generated models"
    dependsOn("compileVerilog")

    doLast {
        var hasFailure = false

        testsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { testDir ->
            val objDir = layout.buildDirectory.dir("tests/${testDir.name}/cpp").get().asFile
            val cppFiles = testDir.listFiles { f -> f.isFile && f.extension.lowercase() == "cpp" }?.toList().orEmpty()

            // Determine top module (same logic as earlier)
            val topFromFile = File(testDir, "top.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
            val gaplFiles = testDir.listFiles { f -> f.isFile && f.extension == "gapl" }?.toList().orEmpty()
            val topFromSingleGapl = if (gaplFiles.size == 1) gaplFiles.first().nameWithoutExtension else null
            val top = topFromFile ?: topFromSingleGapl

            if (top.isNullOrBlank()) {
                println("⚠️  Could not determine top module for '${testDir.name}'. Provide tests/${testDir.name}/top.txt or use a single .gapl file.")
                hasFailure = true
                return@forEach
            }

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

            println("Building and running test for '${testDir.name}' (top=$top)")

            // Use Verilator to build the executable directly, leveraging the generated model and wrapper(s)
            val exeName = "test_${testDir.name}"
            val buildErr = ByteArrayOutputStream()
            val cmd = mutableListOf<String>().apply {
                addAll(listOf("verilator", "-Wall", "-Wno-UNUSEDSIGNAL", "-cc"))
                addAll(verilogFiles.map { it.absolutePath })
                addAll(listOf("--top-module", top, "-Mdir", objDir.absolutePath))
                add("--exe")
                addAll(cppFiles.map { it.absolutePath })
                addAll(listOf("--build", "-o", exeName))
            }
            val buildRes = exec {
                isIgnoreExitValue = true
                commandLine(cmd)
                errorOutput = buildErr
                standardOutput = System.out
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

            val exe = File(objDir, exeName)
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
                standardOutput = System.out
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
