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

fun retime(projectRoot: File, delayModel: File, retimingClockPeriod: Int, programs: List<Program>) {
    println("Minimizing clock periods...")
    println("Using delay model: ${delayModel.absolutePath}")

    programs.forEach { program ->
        program.variants.forEach { variant ->
            NetFPGABuilder(
                runDirectory = projectRoot,
                netFPGAProgramName = program.name,
                netFPGAProgramVariationName = variant.name,
                logHandler = LogHandler(projectRoot.resolve("latency-finder-logs").resolve(program.name).resolve(variant.name)),
                clockPeriod = retimingClockPeriod,
                retime = delayModel,
                retimingClockPeriod = retimingClockPeriod,
                retimingMinimizeRegisterCount = true,
            ).run()
        }
    }
}

fun build(
    projectRoot: File,
    programs: List<Program>
) {
    programs.forEach { program ->
        program.variants.forEach { variant ->
            NetFPGABuilder(
                runDirectory = projectRoot,
                netFPGAProgramName = program.name,
                netFPGAProgramVariationName = variant.name,
                logHandler = LogHandler(projectRoot.resolve("latency-finder-logs").resolve(program.name).resolve(variant.name)),
                clockPeriod = variant.clockPeriod,
                retime = null,
                retimingClockPeriod = null,
                retimingMinimizeRegisterCount = null,
            ).run()
        }
    }
}

data class ProgramVariant(
    val name: String,
    val clockPeriod: Int? = 10,
)

data class Program(
    val name: String,
    val variants: List<ProgramVariant>,
) {
    companion object {
        fun fromClockPeriod(name: String, variants: List<Pair<String, Int>>): Program {
            return Program(name, variants.map { ProgramVariant(it.first, it.second) })
        }

        fun from(name: String, variants: List<String>): Program {
            return Program(name, variants.map { ProgramVariant(it) })
        }

        fun fromClockPeriod(programs: List<Pair<String, List<Pair<String, Int>>>>): List<Program> {
            return programs.map { fromClockPeriod(it.first, it.second) }
        }

        fun from(programs: List<Pair<String, List<String>>>): List<Program> {
            return programs.map { from(it.first, it.second) }
        }
    }
}

fun main(args: Array<String>) {
    val projectRoot = File(".").canonicalFile.parentFile

    /*
    val subprojectRoot = File(".").canonicalFile
    val delayModel = subprojectRoot.resolve("delay.yaml")
     */

    val programs = Program.from(listOf(
        "cms" to listOf(
            "hierarchical-test-unretimed-hierarchical",
            "hierarchical-test-unretimed-monolith",
            /*
            "hierarchical-test-retimed-hierarchical",
            "hierarchical-test-retimed-monolith",
            */
        ),
        /*
        "regex" to listOf(
            "hierarchical-test-unretimed-hierarchical",
            "hierarchical-test-unretimed-monolith",
        ),
        "md5" to listOf(
            "hierarchical-test-retimed-hierarchical",
            "hierarchical-test-retimed-monolith",
            "hierarchical-test-unretimed-hierarchical",
            "hierarchical-test-unretimed-monolith",
        ),
        "aes" to listOf(
            "hierarchical-test-unretimed-hierarchical",
            "hierarchical-test-unretimed-monolith",
            "hierarchical-test-retimed-hierarchical",
            "hierarchical-test-retimed-monolith",
        ),
         */
    ))

    /*
    val programs = Program.fromClockPeriod(listOf(
        "aes" to listOf(
            /*
            "clockperiod-test-retimed-hierarchical-10" to 8,
            "clockperiod-test-retimed-hierarchical-10" to 9,
            "clockperiod-test-retimed-hierarchical-10" to 10,
            "clockperiod-test-retimed-hierarchical-10" to 11,
            "clockperiod-test-retimed-hierarchical-10" to 12,
            "clockperiod-test-retimed-hierarchical-5" to 8,
            "clockperiod-test-retimed-hierarchical-5" to 9,
            "clockperiod-test-retimed-hierarchical-5" to 10,
            "clockperiod-test-retimed-hierarchical-5" to 11,
            "clockperiod-test-retimed-hierarchical-5" to 12,
             */
            "clockperiod-test-retimed-hierarchical-1" to 10,
            "clockperiod-test-retimed-hierarchical-2" to 10,
            "clockperiod-test-retimed-hierarchical-3" to 10,
            "clockperiod-test-retimed-hierarchical-4" to 10,
            "clockperiod-test-retimed-hierarchical-2" to 11,
            "clockperiod-test-retimed-hierarchical-2" to 9,
            "clockperiod-test-retimed-hierarchical-1" to 11,
            "clockperiod-test-retimed-hierarchical-1" to 9,
            "clockperiod-test-retimed-hierarchical-3" to 11,
            "clockperiod-test-retimed-hierarchical-3" to 9,
            "clockperiod-test-retimed-hierarchical-4" to 11,
            "clockperiod-test-retimed-hierarchical-4" to 9,
        ),
    ))
     */

    build(projectRoot, programs)
}

