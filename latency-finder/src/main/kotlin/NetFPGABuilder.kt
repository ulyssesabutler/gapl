import java.io.File

class NetFPGABuilder(
    val runDirectory: File,
    val logHandler: LogHandler,
    val clockPeriod: Int,
) {

    private fun runCommandInDirectoryAndTeeToLog(
        command: List<String>,
        runDirectory: File,
        logFile: File,
    ): Int {
        val processBuilder = ProcessBuilder(command)
            .directory(runDirectory)
            .redirectErrorStream(true) // merge stderr into stdout so order is preserved

        val process = processBuilder.start()

        // Stream process output to both console and file, live.
        logFile.outputStream().buffered().use { logOut ->
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

    private fun getArgsForClockPeriod(clockPeriod: Int) = buildList {
        add("-PclockPeriodNs=$clockPeriod")
    }

    private fun getGradleCommandForClockPeriod(clockPeriod: Int, command: String) = buildList {
        add("./gradlew")
        addAll(getArgsForClockPeriod(clockPeriod))
        add(command)
    }

    private fun gradleCleanCommand() = listOf("./gradlew", ":netfpga:clean")
    private fun gradleMakeInitCommand(clockPeriod: Int) = getGradleCommandForClockPeriod(clockPeriod, ":netfpga:makeInit")
    private fun gradleRemakeIPsCommand(clockPeriod: Int) = getGradleCommandForClockPeriod(clockPeriod, ":netfpga:remakeIPs")
    private fun gradleBuildCommand(clockPeriod: Int) = getGradleCommandForClockPeriod(clockPeriod, ":netfpga:build")

    private fun runCommand(clockPeriodLog: LogHandler.ClockPeriodLog, command: List<String>): Int {
        clockPeriodLog.log("Running command: ${command.joinToString(" ")}")

        return runCommandInDirectoryAndTeeToLog(command, runDirectory, clockPeriodLog.fullLogFile).also { exitCode ->
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
            gradleMakeInitCommand(clockPeriod),
            gradleRemakeIPsCommand(clockPeriod),
            gradleBuildCommand(clockPeriod),
        )

        var failed = false

        for (command in commands) {
            failed = runCommand(clockPeriodLogHandler, command) != 0
            if (failed) break
        }

        logHandler.log("Build for clock period $clockPeriod ${if (failed) "failed" else "succeeded"}")

        logReports(clockPeriodLogHandler)
        if (!failed) logBitstream(clockPeriodLogHandler)

        return !failed
    }

}