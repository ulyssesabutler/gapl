plugins {
    base
    kotlin("jvm") version "2.0.21"
}

//val vivadoSettings = project.findProperty("vivadoSettings") ?: error("vivadoSettings not defined")

// TODO: We should modify this so we can run any test bench
val tbModule = "tb_uart_controller"

val xsimDir = "xsim.dir"
val waveformDb = layout.buildDirectory.file("waveform.wdb")

val handCodedVerilogSource = fileTree("rtl") { include("**/*.v") }
val testBenchVerilogSource= fileTree("tb") { include("**/*.v") }
val generatedVerilogSource= project(":gapl-example").layout.buildDirectory.dir("verilog")

val verilogCompilerCommon = listOf(
    "xvlog", "-sv", "-v", "0",
    "--work", "work",
    "--incr", "--relax"
)

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
            "xelab", tbModule,
            "-s", "${tbModule}_behav",
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
            "xsim", "${tbModule}_behav",
            "-gui", "-ieeewarnings",
            "-log", simulateLogFile,
            "-wdb", wdbOutFile
        )
    )

    inputs.dir(xsimDir)
    outputs.files(waveformDb)
}

tasks.named("build") {
    dependsOn("simulate")
}

tasks.register("cleanVivado") {
    doLast {
        val vivadoTrash = projectDir.listFiles { file ->
            file.name.matches(Regex("""(xvlog|xelab|xsim|vivado|webtalk).*?\.(pb|log|jou|xml|str|tmp|wdb)"""))
        } ?: emptyArray()

        delete(".Xil")
        delete(xsimDir)
        vivadoTrash.forEach { delete(it) }
    }
}

tasks.named("clean") {
    dependsOn("cleanVivado")
}