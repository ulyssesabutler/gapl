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

val handCodedVerilogSource = fileTree("rtl") { include("**/*.v") }
val testBenchVerilogSource = fileTree("tb") { include("**/*.v") }
val generatedVerilogSource = project(":gapl-example").layout.buildDirectory.dir("verilog")

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

// TODO: We should eliminate the gapl-example subproject. The simtest is a better test of the compiler.
//  Instead, gapl code targetting the Basys should exist in this directory
tasks.register<VivadoTask>("compileVerilog") {
    dependsOn(":gapl-example:generateVerilog")

    val handCodedVerilogSourceFiles = handCodedVerilogSource.files.map { it.absolutePath }
    val generatedVerilogSourceFiles = generatedVerilogSource.get().asFileTree.files.map { it.absolutePath }

    doFirst {
        println("Compiling verilog for simulation:")
        (handCodedVerilogSourceFiles + generatedVerilogSourceFiles).forEach { println("  $it") }
    }

    vivadoCommand.set(
        verilogCompilerCommon +
        handCodedVerilogSourceFiles +
        generatedVerilogSourceFiles
    )

    inputs.files(handCodedVerilogSource, generatedVerilogSource)
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