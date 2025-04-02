plugins {
    base
}

tasks.register("generateVerilog") {
    dependsOn(":compiler:installDist")

    // TODO: For now, each GAPL file is mapped to a single verilog file. This means no cross-file dependencies.
    val gaplFiles = fileTree("src") {
        include("**/*.gapl")
    }
    val verilogOutputDir = layout.buildDirectory.dir("compiledVerilog")

    inputs.files(gaplFiles)
    outputs.dir(verilogOutputDir)

    doLast {
        val compiler = project(":compiler")
            .layout.buildDirectory.file("install/gapl/bin/gapl").get().asFile
        val output = verilogOutputDir.get().asFile

        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
        }

        gaplFiles.forEach { gaplFile ->
            val verilogFile = output.resolve(gaplFile.nameWithoutExtension + ".v")
            println("Compiling ${gaplFile.name} -> ${verilogFile.name}")
            exec {
                commandLine(compiler, "-i", gaplFile.absolutePath, "-o", verilogFile.absolutePath)
            }
        }
    }
}

tasks.named("build") {
    dependsOn("generateVerilog")
}