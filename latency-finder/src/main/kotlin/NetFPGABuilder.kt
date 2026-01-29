import java.io.BufferedOutputStream
import java.io.File

class NetFPGABuilder(
    val runDirectory: File,
    val netFPGAProgramName: String?,
    val logHandler: LogHandler,
    val clockPeriod: Int,
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

    private fun getArgsForClockPeriod(clockPeriod: Int, programName: String?) = buildList {
        add("-PclockPeriodNs=$clockPeriod")
        if (programName != null) add("-PprogramName=${programName}")
    }

    private fun getGradleCommandForClockPeriod(clockPeriod: Int, programName: String?, command: String) = buildList {
        add("./gradlew")
        addAll(getArgsForClockPeriod(clockPeriod, programName))
        add(command)
    }

    private fun gradleCleanCommand() = listOf("./gradlew", ":netfpga:clean")
    private fun gradleMakeInitCommand(clockPeriod: Int, programName: String?) = getGradleCommandForClockPeriod(clockPeriod, programName, ":netfpga:makeInit")
    private fun gradleRemakeIPsCommand(clockPeriod: Int, programName: String?) = getGradleCommandForClockPeriod(clockPeriod, programName, ":netfpga:remakeIPs")
    private fun gradleBuildCommand(clockPeriod: Int, programName: String?) = getGradleCommandForClockPeriod(clockPeriod, programName, ":netfpga:build")

    private fun runCommand(clockPeriodLog: LogHandler.ClockPeriodLog, command: List<String>): Int {
        clockPeriodLog.log("Running command: ${command.joinToString(" ")}")

        return runCommandInDirectoryAndTeeToLog(command, runDirectory, clockPeriodLog.commandOutStream()).also { exitCode ->
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
            gradleMakeInitCommand(clockPeriod, netFPGAProgramName),
            gradleRemakeIPsCommand(clockPeriod, netFPGAProgramName),
            gradleBuildCommand(clockPeriod, netFPGAProgramName),
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