import SlackFinder
import java.io.File

fun parseArgs(args: Array<String>): Map<String, List<String>> {
    // https://stackoverflow.com/questions/53946908/functional-style-main-function-argument-parsing-for-kotlin
    return args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
        if (elem.startsWith("-"))  Pair(map + (elem to emptyList()), elem)
        else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first
}

fun minimizeClockPeriods(projectRoot: File, programs: List<String>) {
    programs.forEach { programName ->
        ClockPeriodFinder(projectRoot, programName).findClockPeriod()
    }
}

fun retime(projectRoot: File, delayModel: File, retimingClockPeriod: Int, programs: List<String>) {
    programs.forEach { programName ->
        NetFPGABuilder(
            runDirectory = projectRoot,
            netFPGAProgramName = programName,
            logHandler = LogHandler(projectRoot.resolve("latency-finder-logs").resolve(programName)),
            clockPeriod = retimingClockPeriod,
            retime = delayModel,
            retimingClockPeriod = retimingClockPeriod,
            retimingMinimizeRegisterCount = true,
        ).run()
    }
}

fun main(args: Array<String>) {
    val projectRoot = File(".").canonicalFile.parentFile
    val subprojectRoot = File(".").canonicalFile

    val programs = listOf(
        // "cms-unretimed",
        // "md5-unretimed",
        "combinational-aes",
        "regex",
    )

    val delayModel = subprojectRoot.resolve("delay.yaml")

    println("Minimizing clock periods...")
    println("Using delay model: ${delayModel.absolutePath}")

    retime(
        projectRoot = projectRoot,
        delayModel = delayModel,
        retimingClockPeriod = 10,
        programs = programs
    )
}

