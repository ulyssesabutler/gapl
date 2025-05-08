import org.jetbrains.kotlin.org.apache.commons.io.output.ByteArrayOutputStream

plugins {
    base
}

tasks.register("generateVerilog") {
    dependsOn(":compiler:installDist")

    // TODO: For now, each GAPL file is mapped to a single verilog file. This means no cross-file dependencies.
    val gaplFiles = fileTree("src") {
        include("**/*.gapl")
    }
    val verilogOutputDir = layout.buildDirectory.dir("verilog")

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
                commandLine(compiler, "-i", gaplFile.absolutePath, "-o", verilogFile.absolutePath)
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