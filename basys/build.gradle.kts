import java.io.ByteArrayOutputStream

plugins {
    base
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(8)
}

// Change the test bench module with -PtestBenchModule=<moduleName>
val testBenchModule = project.findProperty("testBenchModule")?.toString() ?: throw GradleException("You must provide -PtestBenchModule=<moduleName>")

val xsimDir = "xsim.dir"
val waveformDb = layout.buildDirectory.file("waveform.wdb")


val gaplFiles = fileTree("src") {
    include("**/*.gapl")
}

val gaplVerilogDir = layout.buildDirectory.dir("verilog")

val handCodedVerilogSource = fileTree("rtl") { include("**/*.v") }
val testBenchVerilogSource = fileTree("tb") { include("**/*.v") }

val runName = findProperty("runName")?.toString() ?: "run1"

val synthesisName = "synthesis_$runName"
val implementation1Name = "impl_s1_opt_design_$runName"
val implementation2Name = "impl_s2_power_opt_design_$runName"
val implementation3Name = "impl_s3_place_design_$runName"
val implementation4Name = "impl_s4_phys_opt_design_$runName"
val implementation5Name = "impl_s5_route_design_$runName"
val bitstreamName = "bitstream_$runName"

val verilogCompilerCommon = listOf(
    "xvlog", "-sv", "-v", "0",
    "--work", "work",
    "--incr", "--relax"
)

tasks.register<VivadoTask>("setupProject") {
    vivadoCommand.set(
        listOf("vivado", "-mode", "tcl", "-source", "scripts/setup_project.tcl")
    )
}

tasks.register<Delete>("cleanVivadoRun") {
    delete(".gen", ".srcs")
}

tasks.register<VivadoTask>("runVivado") {
    dependsOn("setupProject")
    dependsOn("cleanVivadoRun")

    vivadoCommand.set(
        listOf(
            "vivado", "-mode", "batch", "-nojournal", "-notrace",
            "-stack 2000", "-source", "./scripts/main.tcl", "-tclargs",
            synthesisName,
            implementation1Name,
            implementation2Name,
            implementation3Name,
            implementation4Name,
            implementation5Name,
            bitstreamName,
        )
    )

    inputs.dir("scripts")
    // TODO: Outputs
}

tasks.register("compileGapl") {
    group = "gapl"
    description = "Compile .gapl sources in basys/src to Verilog into build/verilog"
    dependsOn(":compiler:installDist")

    inputs.files(gaplFiles)
    outputs.dir(gaplVerilogDir)

    doLast {
        val compiler = project(":compiler")
            .layout.buildDirectory.file("install/gapl/bin/gapl").get().asFile
        val outDir = gaplVerilogDir.get().asFile

        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
        }
        outDir.mkdirs()

        var hasFailure = false

        gaplFiles.forEach { gaplFile ->
            val verilogFile = outDir.resolve(gaplFile.nameWithoutExtension + ".v")
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
            throw GradleException("One or more GAPL files failed to compile")
        }
    }
}

tasks.register<VivadoTask>("compileVerilog") {
    group = "simulation"
    description = "Compile Verilog (hand-coded + GAPL-generated) for simulation"

    dependsOn("compileGapl")

    val generatedVerilogFiles = gaplVerilogDir.map { dir ->
        dir.asFileTree.matching { include("**/*.v") }
    }

    doFirst {
        println("Compiling verilog for simulation:")

        handCodedVerilogSource.files
            .map { it.absolutePath }
            .sorted()
            .forEach { println("  $it") }

        generatedVerilogFiles.get().files
            .map { it.absolutePath }
            .sorted()
            .forEach { println("  $it") }
    }

    vivadoCommand.set(provider {
        val hand = handCodedVerilogSource.files.map { it.absolutePath }
        val gen  = generatedVerilogFiles.get().files.map { it.absolutePath }
        verilogCompilerCommon + hand + gen
    })

    inputs.files(handCodedVerilogSource, generatedVerilogFiles)
    outputs.dir(xsimDir)
}

tasks.register<VivadoTask>("compileTestBench") {
    dependsOn("compileVerilog")

    val testBenchVerilogSourceFiles = testBenchVerilogSource.files.map { it.absolutePath }

    doFirst {
        println("Compiling test bench for simulation:")
        testBenchVerilogSourceFiles.forEach { println("  $it") }
    }

    vivadoCommand.set(
        verilogCompilerCommon +
        testBenchVerilogSourceFiles
    )

    inputs.files(testBenchVerilogSource)
    outputs.dir(xsimDir)
}

tasks.register<VivadoTask>("elaborate") {
    dependsOn("compileVerilog")
    dependsOn("compileTestBench")

    val elaborateLogFile = layout.buildDirectory.file("elaborate.log").get().asFile.absolutePath

    vivadoCommand.set(
        listOf(
            "xelab", testBenchModule,
            "-s", "${testBenchModule}_behav",
            "--incr", "--debug", "typical", "--relax", "--mt", "8",
            "-L", "work",
            "-log", elaborateLogFile,
        )
    )

    inputs.dir(xsimDir)
    outputs.files(waveformDb)
}

tasks.register<VivadoTask>("simulate") {
    dependsOn("elaborate")

    val simulateLogFile = layout.buildDirectory.file("simulate.log").get().asFile.absolutePath
    val wdbOutFile = waveformDb.get().asFile.absolutePath

    vivadoCommand.set(
        listOf(
            "xsim", "${testBenchModule}_behav",
            "-gui", "-ieeewarnings",
            "-log", simulateLogFile,
            "-wdb", wdbOutFile
        )
    )

    inputs.dir(xsimDir)
    outputs.files(waveformDb)
}

tasks.named("build") {
    dependsOn("runVivado")
}

tasks.register<Delete>("cleanVivado") {
    dependsOn("cleanVivadoRun")

    val bitstream = "$bitstreamName.bit"

    delete(
        ".Xil", "bin", "clockInfo.txt", xsimDir, bitstream,
        fileTree(".") { include("*.pb", "*.log", "*.jou") }
    )
}

tasks.named("clean") {
    dependsOn("cleanVivado")
}