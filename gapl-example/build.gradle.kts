import org.jetbrains.kotlin.org.apache.commons.io.output.ByteArrayOutputStream

plugins {
    base
}

tasks.register("generateVerilog") {
    // TODO: For now, each GAPL file is mapped to a single verilog file. This means no cross-file dependencies.
    val gaplFiles = fileTree("src") {
        include("**/*.gapl")
    }
    val verilogOutputDir = layout.buildDirectory.dir("verilog")

    // The compiler distribution is a real input, not just an ordering dependency. `dependsOn` alone
    // guarantees installDist runs first but does not dirty this task when the compiler changes, so a
    // compiler edit used to leave already-generated Verilog UP-TO-DATE - silently validating stale
    // output, which is worse than failing. A task provider resolves to that task's outputs *and*
    // carries the dependency, so it replaces the `dependsOn` outright.
    inputs.files(project(":compiler").tasks.named("installDist"))
    inputs.files(gaplFiles)
    outputs.dir(verilogOutputDir)

    doLast {
        val compiler = project(":compiler")
            .layout.buildDirectory.file("install/gapl/bin/gapl").get().asFile
        val output = verilogOutputDir.get().asFile

        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
        }

        var hasFailure = false

        gaplFiles.forEach { gaplFile ->
            val verilogFile = output.resolve(gaplFile.nameWithoutExtension + ".v")
            println("Compiling ${gaplFile.name} -> ${verilogFile.name}")

            val errorOut = ByteArrayOutputStream()

            val result = exec {
                isIgnoreExitValue = true
                commandLine(compiler, gaplFile.absolutePath, "--output", verilogFile.absolutePath)
                errorOutput = errorOut
                standardOutput = System.out
            }

            if (result.exitValue != 0) {
                hasFailure = true
                println("❌ Failed to compile ${gaplFile.name}")
                println("---- Compiler Error Output ----")
                println(errorOut.toString().trim())
                println("-------------------------------")
            }
        }

        if (hasFailure) {
            throw GradleException("One or more gapl files failed to compile")
        }
    }
}

tasks.named("build") {
    dependsOn("generateVerilog")
}