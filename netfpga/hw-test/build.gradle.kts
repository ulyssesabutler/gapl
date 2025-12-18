import java.util.Properties

plugins {
    base
}

val executableName = "generator"

val sources = listOf(
    "main.cpp",
    "network/packet.cpp",
    "network/socket.cpp",
    "util/hex.cpp",
    "util/options.cpp",
    "util/string_utils.cpp",
)

val generatorBinary = layout.buildDirectory.file("bin/$executableName")

val generatorConfigFile = project.file("generator.properties")

val programName = findProperty("programName")!! as String

val gaplSrcRoot = layout.projectDirectory.dir("../src/$programName").asFile

val testPropsFile = gaplSrcRoot.resolve("test.properties")
val testProps = Properties().apply {
    testPropsFile.inputStream().use { load(it) }
}

val testInputs = testProps.getProperty("testInputs")
val testExpectedOutputs = testProps.getProperty("testExpectedOutputs")

tasks.register<Exec>("buildTest") {
    group = "build"
    description = "Compile C++ traffic generator"

    inputs.files(sources.map { file(it) })
    outputs.file(generatorBinary)

    doFirst {
        val outFile = generatorBinary.get().asFile
        outFile.parentFile.mkdirs()

        val cmd = mutableListOf(
            "g++",
            "-pthread",
            "-o",
            outFile.absolutePath
        ).apply {
            addAll(sources)
        }

        commandLine(cmd)
    }
}

tasks.named("build") {
    dependsOn("buildTest")
}

fun loadGeneratorArgsFromConfig(): List<String> {
    if (!generatorConfigFile.exists()) {
        throw GradleException(
            "Config file not found: ${generatorConfigFile.path}. " +
                    "Create it with keys: txInterface, rxInterface, srcIp, dstIp, port."
        )
    }

    val props = Properties().apply {
        generatorConfigFile.inputStream().use { load(it) }
    }

    fun prop(name: String): String =
        props.getProperty(name)
            ?: throw GradleException("Missing '$name' in ${generatorConfigFile.path}")

    val args = listOf(
        "-t", prop("transmittingInterface"),
        "-r", prop("receivingInterface"),
        "-s", prop("sourceIP"),
        "-d", prop("destinationIP"),
        "-p", prop("port"),
    )

    val inputs = (testInputs?.split(",") ?: emptyList()).flatMap { listOf("-i", it) }
    val expectedOutputs = (testExpectedOutputs?.split(",") ?: emptyList()).flatMap { listOf("-o", it) }

    return args + inputs + expectedOutputs
}

tasks.register<Exec>("runTest") {
    group = "application"
    description = "Run the traffic generator using generator.properties"
    dependsOn("buildTest")

    // Changing the config should make Gradle rerun this
    inputs.file(generatorConfigFile)

    workingDir = projectDir

    doFirst {
        val argsFromConfig = loadGeneratorArgsFromConfig()
        val exe = generatorBinary.get().asFile.absolutePath

        val cmd = buildList {
            add(exe)
            addAll(argsFromConfig)
        }

        println("Running: ${cmd.joinToString(" ")}")
        commandLine(cmd)
    }
}
