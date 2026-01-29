import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

class ClockPeriodFinder(
    val projectRoot: File,
    val netFPGAProgramName: String? = null,
    val logHandler: LogHandler = LogHandler(projectRoot.resolve("latency-finder-logs").resolve(netFPGAProgramName ?: "default")),
    val minClockPeriod: Int = 10,
) {

    val json = Json { prettyPrint = true }

    @Serializable
    private data class ClockPeriodResult(
        val clockPeriod: Int,
        var buildComplete: Boolean,
        var feasible: Boolean?,
        var slack: Double?,
    )

    private data class CompletedClockPeriodResult(
        val clockPeriod: Int,
        var feasible: Boolean,
        var slack: Double,
    )

    private val clockPeriodResults = mutableListOf<ClockPeriodResult>()
    private fun completedClockPeriodResult() = clockPeriodResults.filter { it.buildComplete }.map { CompletedClockPeriodResult(it.clockPeriod, it.feasible!!, it.slack!!) }
    val clockPeriodsFileName = "clock-periods.json"

    fun serialize() {
        logHandler.writeStringToFile(clockPeriodsFileName, json.encodeToString(clockPeriodResults))
    }

    fun attemptDeserialize() {
        try {
            val clockPeriods = json.decodeFromString<List<ClockPeriodResult>>(logHandler.readStringFromFile(clockPeriodsFileName))
            clockPeriodResults.addAll(clockPeriods)
            logHandler.log("Deserialized existing run")
        } catch (e: Exception) {
            logHandler.log("Failed to deserialize clock periods: ${e.message}")
        }
    }

    private fun attemptClockPeriod(clockPeriod: Int): ClockPeriodResult {
        if (completedClockPeriodResult().any { it.clockPeriod == clockPeriod }) return clockPeriodResults.first { it.clockPeriod == clockPeriod }

        val builder = NetFPGABuilder(
            runDirectory = projectRoot,
            netFPGAProgramName = netFPGAProgramName,
            logHandler = logHandler,
            clockPeriod = clockPeriod,
        )

        val clockPeriodResult = ClockPeriodResult(clockPeriod, false, null, null)
        clockPeriodResults.add(clockPeriodResult)
        serialize()

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

        clockPeriodResult.feasible = criticalSlack.met
        clockPeriodResult.slack = criticalSlack.slack.absoluteValue
        clockPeriodResult.buildComplete = true

        serialize()

        return clockPeriodResult
    }

    private fun optimalClockPeriod(): Int? {
        if (completedClockPeriodResult().firstOrNull { it.clockPeriod == minClockPeriod }?.feasible ?: false) return minClockPeriod

        val sorted = completedClockPeriodResult().sortedBy { it.clockPeriod }

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
        val smallestFeasible = completedClockPeriodResult()
            .filter { it.feasible }
            .minByOrNull { it.clockPeriod }

        val largestInfeasible = completedClockPeriodResult()
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
                return smallestFeasible.clockPeriod - max(1, smallestFeasible.slack.roundToInt())

            return smallestFeasible.clockPeriod - 1
        }

        if (smallestFeasible != null)
            return smallestFeasible.clockPeriod - max(1, smallestFeasible.slack.roundToInt())

        return largestInfeasible!!.clockPeriod + max(1, largestInfeasible.slack.roundToInt())
    }

    fun findClockPeriod(): Int {
        var optimalClockPeriod: Int? = optimalClockPeriod()

        attemptDeserialize()

        while (true) {
            if (optimalClockPeriod != null) break

            attemptClockPeriod(nextClockPeriod())
            optimalClockPeriod = optimalClockPeriod()
        }

        logHandler.log("Optimal clock period: $optimalClockPeriod")

        completedClockPeriodResult()
            .sortedBy { it.clockPeriod }
            .forEach {
                logHandler.log("Clock period ${it.clockPeriod}: feasible: ${it.feasible}, slack: ${it.slack}")
            }

        return optimalClockPeriod
    }

}