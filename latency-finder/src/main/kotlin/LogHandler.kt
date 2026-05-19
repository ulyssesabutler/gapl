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
        val clockPeriod: Int?,
        val directory: File,
        val abridgedLogFile: File = directory.resolve("log.txt"),
        val buildLogFile: File = directory.resolve("build.txt"),
        val testLogFile: File = directory.resolve("test.txt"),
        val reportsDirectory: File = directory.resolve("reports"),
        val bitstreamDirectory: File = directory.resolve("bitstream"),
    ) {
        init {
            directory.mkdirs()
            reportsDirectory.mkdirs()
            bitstreamDirectory.mkdirs()

            abridgedLogFile.createNewFile()
            buildLogFile.createNewFile()
            testLogFile.createNewFile()
        }

        fun log(message: String) {
            abridgedLogFile.appendText("$message\n")
        }

        fun saveBitstream(bitstreamFile: File) = bitstreamFile.copyTo(bitstreamDirectory.resolve(bitstreamFile.name), overwrite = true)

        fun saveReports(reportDirectory: File) = reportDirectory.copyRecursively(reportsDirectory, overwrite = true)

        fun buildLogStream() = buildLogFile.outputStream().buffered()
        fun testLogStream() = testLogFile.outputStream().buffered()
    }

    private val logs = mutableMapOf<Int?, ClockPeriodLog>()

    fun getLog(clockPeriod: Int?) = logs.getOrPut(clockPeriod) { ClockPeriodLog(clockPeriod, logDirectory.resolve("clock-period-${clockPeriod ?: "default"}")) }

    fun log(message: String) {
        abridgedLogFile.appendText("$message\n")
    }

    fun writeStringToFile(fileName: String, content: String) {
        val file = File(logDirectory, fileName)
        file.createNewFile()

        file.writeText(content)
    }

    fun readStringFromFile(fileName: String): String {
        return File(logDirectory, fileName).readText()
    }

}