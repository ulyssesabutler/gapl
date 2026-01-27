import SlackFinder
import java.io.File

fun parseArgs(args: Array<String>): Map<String, List<String>> {
    // https://stackoverflow.com/questions/53946908/functional-style-main-function-argument-parsing-for-kotlin
    return args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
        if (elem.startsWith("-"))  Pair(map + (elem to emptyList()), elem)
        else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first
}

fun main(args: Array<String>) {
    val projectRoot = File(".").canonicalFile.parentFile

    val logHandler = LogHandler(projectRoot.resolve("latency-finder-logs"))

    ClockPeriodFinder(projectRoot, logHandler).findClockPeriod()
}

