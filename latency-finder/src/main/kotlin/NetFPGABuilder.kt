import java.io.BufferedOutputStream
import java.io.File

class NetFPGABuilder(
    val runDirectory: File,
    val netFPGAProgramName: String?,
    val logHandler: LogHandler,
    val clockPeriod: Int,
    val retime: File? = null,
    val retimingClockPeriod: Int? = null,
    val retimingMinimizeRegisterCount: Boolean? = null,
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
                    logOut.write((line + "\n").toByteArray(Charsets.UTF_8))
                    logOut.flush()
                }
            }
        }

        val exitCode = process.waitFor()

        return exitCode
    }

    private fun getArgsForClockPeriod(programName: String?) = buildList {
        add("-PclockPeriodNs=$clockPeriod")
        add("-PnetfpgaSimTestGui=false")
        if (programName != null) add("-PprogramName=${programName}")

        if (retime != null) add("-PdelayModelPath=${retime.absolutePath}")
        if (retimingClockPeriod != null) add("-PretimingClockPeriod=$retimingClockPeriod")
        if (retimingMinimizeRegisterCount != null) add("-PretimingMinimizeRegisterCount=$retimingMinimizeRegisterCount")
    }

    private fun getGradleCommandForClockPeriod(
        programName: String?,
        command: String,
    ) = buildList {
        add("./gradlew")
        addAll(getArgsForClockPeriod(programName))
        add(command)
    }

    private fun gradleCleanCommand() = listOf("./gradlew", ":netfpga:clean")
    private fun gradleMakeInitCommand(programName: String?) = getGradleCommandForClockPeriod(programName, ":netfpga:makeInit")
    private fun gradleRemakeIPsCommand(programName: String?) = getGradleCommandForClockPeriod(programName, ":netfpga:remakeIPs")
    private fun gradleBuildCommand(programName: String?) = getGradleCommandForClockPeriod(programName, ":netfpga:build")
    private fun gradleTestCommand(programName: String?) = getGradleCommandForClockPeriod(programName, ":netfpga:runSimulation")

    private fun runCommand(clockPeriodLog: LogHandler.ClockPeriodLog, command: List<String>, logStream: BufferedOutputStream): Int {
        clockPeriodLog.log("Running command: ${command.joinToString(" ")}")

        return runCommandInDirectoryAndTeeToLog(command, runDirectory, logStream).also { exitCode ->
            clockPeriodLog.log("Command exited with code $exitCode")
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
        logHandler.log("Building for clock period $clockPeriod")

        val clockPeriodLogHandler = logHandler.getLog(clockPeriod)

        val commands = listOf(
            gradleCleanCommand(),
            gradleMakeInitCommand(netFPGAProgramName),
            gradleRemakeIPsCommand(netFPGAProgramName),
            gradleBuildCommand(netFPGAProgramName),
        )

        var failed = false

        for (command in commands) {
            failed = runCommand(clockPeriodLogHandler, command, clockPeriodLogHandler.buildLogStream()) != 0
            if (failed) break
        }

        if (!failed) {
            val testCommand = gradleTestCommand(netFPGAProgramName)
            failed = runCommand(clockPeriodLogHandler, testCommand, clockPeriodLogHandler.testLogStream()) != 0
        }

        logHandler.log("Build for clock period $clockPeriod ${if (failed) "failed" else "succeeded"}")

        logReports(clockPeriodLogHandler)
        if (!failed) logBitstream(clockPeriodLogHandler)

        return !failed
    }

}