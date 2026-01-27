import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class ClockPeriodFinder(
    val projectRoot: File,
    val logHandler: LogHandler = LogHandler(projectRoot.resolve("latency-finder-logs")),
    val minClockPeriod: Int = 10,
) {

    private data class ClockPeriodResult(
        val clockPeriod: Int,
        val feasible: Boolean,
        val slack: Double,
    )

    private val clockPeriodResults = mutableListOf<ClockPeriodResult>()

    private fun attemptClockPeriod(clockPeriod: Int): ClockPeriodResult {
        if (clockPeriodResults.any { it.clockPeriod == clockPeriod }) return clockPeriodResults.first { it.clockPeriod == clockPeriod }

        val builder = NetFPGABuilder(
            runDirectory = projectRoot,
            logHandler = logHandler,
            clockPeriod = clockPeriod,
        )

        builder.run()

        val criticalSlack = SlackFinder.getCriticalSlack(projectRoot)

        val clockPeriodLog = logHandler.getLog(clockPeriod)

        clockPeriodLog.log("Critical Slack: feasible: ${criticalSlack.met}, slack: ${criticalSlack.slack}")
        clockPeriodLog.log("From Line:                ${criticalSlack.line}")

        if (criticalSlack.met) {
            clockPeriodLog.log("SUCCESS")
        } else {
            clockPeriodLog.log("FAILURE")
        }

        clockPeriodLog.log("All slack lines:")
        SlackFinder.getSlack(projectRoot).forEach { slack ->
            clockPeriodLog.log("  ${slack.line}")
        }

        return ClockPeriodResult(clockPeriod, criticalSlack.met, criticalSlack.slack.absoluteValue)
    }

    private fun optimalClockPeriod(): Int? {
        if (clockPeriodResults.firstOrNull { it.clockPeriod == minClockPeriod }?.feasible ?: false) return minClockPeriod

        val sorted = clockPeriodResults.sortedBy { it.clockPeriod }

        if (sorted.size < 2) return null

        sorted.zipWithNext().forEach { (smaller, larger) ->
            val oneApart = (larger.clockPeriod - smaller.clockPeriod) == 1
            val smallerInfeasible = !smaller.feasible
            val largerFeasible = larger.feasible

            if (oneApart && smallerInfeasible && largerFeasible) return larger.clockPeriod
        }

        return null
    }

    private fun nextClockPeriod(): Int {
        val smallestFeasible = clockPeriodResults
            .filter { it.feasible }
            .minByOrNull { it.clockPeriod }

        val largestInfeasible = clockPeriodResults
            .filter { !it.feasible }
            .maxByOrNull { it.clockPeriod }

        logHandler.log("Smallest feasible: ${smallestFeasible?.clockPeriod}, Largest infeasible: ${largestInfeasible?.clockPeriod}")

        if (smallestFeasible == null && largestInfeasible == null) {
            return minClockPeriod
        }

        if (smallestFeasible != null && largestInfeasible != null) {
            if (smallestFeasible.slack < largestInfeasible.slack)
                return smallestFeasible.clockPeriod - 1

            val clockPeriodRange = smallestFeasible.clockPeriod - largestInfeasible.clockPeriod
            if (clockPeriodRange > 3 && smallestFeasible.slack.roundToInt() < clockPeriodRange)
                return smallestFeasible.clockPeriod - smallestFeasible.slack.roundToInt()

            return smallestFeasible.clockPeriod - 1
        }

        if (smallestFeasible != null)
            return smallestFeasible.clockPeriod - smallestFeasible.slack.roundToInt()

        return largestInfeasible!!.clockPeriod + largestInfeasible.slack.roundToInt()
    }

    fun findClockPeriod(): Int {
        var optimalClockPeriod: Int? = optimalClockPeriod()

        while (true) {
            if (optimalClockPeriod != null) break

            attemptClockPeriod(nextClockPeriod())
            optimalClockPeriod = optimalClockPeriod()
        }

        logHandler.log("Optimal clock period: $optimalClockPeriod")

        clockPeriodResults
            .sortedBy { it.clockPeriod }
            .forEach {
                logHandler.log("Clock period ${it.clockPeriod}: feasible: ${it.feasible}, slack: ${it.slack}")
            }

        return optimalClockPeriod
    }

}