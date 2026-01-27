import java.io.File

class LogHandler(
    val logDirectory: File,
    val abridgedLogFile: File = logDirectory.resolve("log.txt"),
) {

    init {
        logDirectory.mkdirs()
        abridgedLogFile.createNewFile()
    }

    data class ClockPeriodLog(
        val clockPeriod: Int,
        val directory: File,
        val abridgedLogFile: File = directory.resolve("log.txt"),
        val fullLogFile: File = directory.resolve("commandout.txt"),
        val reportsDirectory: File = directory.resolve("reports"),
        val bitstreamDirectory: File = directory.resolve("bitstream"),
    ) {
        init {
            directory.mkdirs()
            reportsDirectory.mkdirs()
            bitstreamDirectory.mkdirs()

            abridgedLogFile.createNewFile()
            fullLogFile.createNewFile()
        }

        fun log(message: String) {
            abridgedLogFile.appendText("$message\n")
        }

        fun saveBitstream(bitstreamFile: File) = bitstreamFile.copyTo(bitstreamDirectory.resolve(bitstreamFile.name), overwrite = true)

        fun saveReports(reportDirectory: File) = reportDirectory.copyRecursively(reportsDirectory, overwrite = true)

        fun commandOutStream() = fullLogFile.outputStream().buffered()
    }

    private val logs = mutableMapOf<Int, ClockPeriodLog>()

    fun getLog(clockPeriod: Int) = logs.getOrPut(clockPeriod) { ClockPeriodLog(clockPeriod, logDirectory.resolve("clock-period-$clockPeriod")) }

    fun log(message: String) {
        abridgedLogFile.appendText("$message\n")
    }

}