import java.io.File
import kotlin.math.absoluteValue

object SlackFinder {
    data class Slack(
        val line: String,
        val met: Boolean,
        val slack: Double,
    )

    private fun getTimingReportFile(projectRoot: File) = File(
        projectRoot,
        "netfpga/packet-processor/projects/reference_switch/hw/project/reference_switch.runs/impl_1/top_timing_summary_routed.rpt"
    )

    fun getSlack(projectRoot: File): List<Slack> {
        val timingReport = getTimingReportFile(projectRoot)
        require(timingReport.isFile) { "Not a readable file: ${timingReport.absolutePath}" }

        val slackLineRegex = Regex(
            pattern = """^\s*Slack\s*\((MET|VIOLATED)\)\s*:\s*([+-]?\d+(?:\.\d+)?)\s*ns\b""",
            option = RegexOption.IGNORE_CASE
        )

        return timingReport.useLines { lines ->
            lines.mapNotNull { line ->
                val match = slackLineRegex.find(line) ?: return@mapNotNull null
                val status = match.groupValues[1].uppercase()
                val slackValue = match.groupValues[2].toDouble()

                Slack(
                    line = line,
                    met = (status == "MET"),
                    slack = slackValue,
                )
            }.toList()
        }
    }

    fun getCriticalSlack(projectRoot: File): Slack {
        val allSlack = getSlack(projectRoot)

        if (allSlack.isEmpty()) throw IllegalStateException()

        val failingSlack = allSlack.filter { !it.met }

        return if (failingSlack.isNotEmpty()) {
            failingSlack.maxBy { it.slack.absoluteValue }
        } else {
            allSlack.minBy { it.slack.absoluteValue }
        }
    }

}