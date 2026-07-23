import java.io.BufferedOutputStream
import java.io.File
import java.time.Instant

class NetFPGABuilder(
    val runDirectory: File,
    val netFPGAProgramName: String?,
    val netFPGAProgramVariationName: String?,
    val logHandler: LogHandler,
    val clockPeriod: Int?,
    val retime: File? = null,
    val retimingClockPeriod: Int? = null,
    val retimingSolver: String? = null,
) {

    private fun runCommandInDirectoryAndTeeToLog(
        command: List<String>,
        runDirectory: File,
        logStream: BufferedOutputStream,
    ): Int {
        val processBuilder = ProcessBuilder(command)
            .directory(runDirectory)
            .redirectErrorStream(true) // merge stderr into stdout so order is preserved

        val process = processBuilder.start()

        // Stream process output to both console and file, live.
        logStream.use { logOut ->
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println(line)
                    logOut.write(("[${Instant.now()}] $line\n").toByteArray(Charsets.UTF_8))
                    logOut.flush()
                }
            }
        }

        val exitCode = process.waitFor()

        return exitCode
    }

    private fun getArgsForClockPeriod() = buildList {
        if (clockPeriod != null) add("-PclockPeriodNs=$clockPeriod")
        if (netFPGAProgramName != null) add("-PprogramName=${netFPGAProgramName}")
        if (netFPGAProgramVariationName != null) add("-PprogramVariationName=${netFPGAProgramVariationName}")

        add("-PnetfpgaSimTestGui=false")

        if (retime != null) add("-PdelayModelPath=${retime.absolutePath}")
        if (retimingClockPeriod != null) add("-PretimingClockPeriod=$retimingClockPeriod")
        if (retimingSolver != null) add("-PretimingSolver=$retimingSolver")
    }

    private fun getGradleCommandForClockPeriod(
        command: String,
    ) = buildList {
        add("./gradlew")
        addAll(getArgsForClockPeriod())
        add(command)
    }

    private fun gradleCleanCommand() = listOf("./gradlew", ":netfpga:clean")
    private fun gradlePreGenerateGaplVerilog() = getGradleCommandForClockPeriod(":compiler:installDist")
    private fun gradleGenerateGaplVerilog() = getGradleCommandForClockPeriod(":netfpga:generateGaplVerilog")
    private fun gradleMakeInitCommand() = getGradleCommandForClockPeriod(":netfpga:makeInit")
    private fun gradleRemakeIPsCommand() = getGradleCommandForClockPeriod(":netfpga:remakeIPs")
    private fun gradleBuildCommand() = getGradleCommandForClockPeriod(":netfpga:build")
    private fun gradleTestCommand() = getGradleCommandForClockPeriod(":netfpga:runSimulation")

    private fun runCommand(clockPeriodLog: LogHandler.ClockPeriodLog, command: List<String>, logStream: BufferedOutputStream): Int {
        clockPeriodLog.log("Running command: ${command.joinToString(" ")}")
        val startTime = System.currentTimeMillis()

        return runCommandInDirectoryAndTeeToLog(command, runDirectory, logStream).also { exitCode ->
            val endTime = System.currentTimeMillis()
            clockPeriodLog.log("Command exited with code $exitCode after ${(endTime - startTime) / 1000.0}s")
        }
    }

    private fun getReportDirectory(projectRoot: File) = File(
        projectRoot,
        "netfpga/packet-processor/projects/reference_switch/hw/project"
    )

    private fun getBitsreamFile(projectRoot: File) = File(
        projectRoot,
        "netfpga/packet-processor/projects/reference_switch/bitfiles/reference_switch.bit"
    )

    fun logReports(clockPeriodLog: LogHandler.ClockPeriodLog) {
        clockPeriodLog.log("Saving reports")
        clockPeriodLog.saveReports(getReportDirectory(runDirectory))
    }

    fun logBitstream(clockPeriodLog: LogHandler.ClockPeriodLog) {
        clockPeriodLog.log("Saving bitstream")
        clockPeriodLog.saveBitstream(getBitsreamFile(runDirectory))
    }

    fun run(): Boolean {
        logHandler.log("Building for clock period ${clockPeriod ?: "default"}")

        val clockPeriodLogHandler = logHandler.getLog(clockPeriod)

        val commands = listOf(
            gradleCleanCommand(),
            gradlePreGenerateGaplVerilog(),
            gradleGenerateGaplVerilog(),
            gradleMakeInitCommand(),
            gradleRemakeIPsCommand(),
            gradleBuildCommand(),
        )

        var failed = false

        for (command in commands) {
            failed = runCommand(clockPeriodLogHandler, command, clockPeriodLogHandler.buildLogStream()) != 0
            if (failed) break
        }

        /*
        if (!failed) {
            val testCommand = gradleTestCommand()
            failed = runCommand(clockPeriodLogHandler, testCommand, clockPeriodLogHandler.testLogStream()) != 0
        }
         */

        logHandler.log("Build for clock period ${clockPeriod ?: "default"} ${if (failed) "failed" else "succeeded"}")

        logReports(clockPeriodLogHandler)
        if (!failed) logBitstream(clockPeriodLogHandler)

        return !failed
    }

}